package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 铁砧锤 + 热键（默认左 Ctrl）<b>右键</b>电线杆 → 打开那根杆专属的重连排序配置页。
 *
 * <h2>为什么这里必须取消这次右键</h2>
 * <p>本体的铁砧锤走 {@code client/event/ClientBlockEventListener}（优先级 {@code HIGH}）：
 * 它在客户端命中方块后<b>主动发一个 {@code HammerUsePacket}</b>，服务端收到后调用
 * {@code AnvilHammerItem.useBlock} → {@code PoleHammerBehavior.change} <b>切换锁定</b>。
 * 这次右键的语义完全由客户端那个监听器决定，服务端并不知道玩家按没按热键。
 * 因此在客户端 {@code HIGHEST} 上取消，普通右键切锁定与热键右键开页面才能共用一个按键。</p>
 *
 * <h2>为什么开屏要延后一拍（V1.12.14）</h2>
 * <p>只靠「取消事件」并不可靠：本体的 {@code clientHandle} 会在同一次右键里检查
 * {@code AnvilHammerItem.findModifyableProperty(targetBlockState)}，而我们的输电杆继承了带
 * {@code SWITCH} 的 {@code RemoteTransmissionPoleBlock}，于是它<b>也会</b>
 * {@code Minecraft.setScreen(new AnvilHammerScreen(...))}。结果是两个界面叠在一起。</p>
 *
 * <p>现在的做法是：事件里照旧取消（能省掉的副作用就省掉），但只把「要开哪一页」记下来，
 * 真正的 {@code setScreen} 放到 <b>下一个 {@link ClientTickEvent.Post}</b>。之所以不用
 * {@link Minecraft#execute(Runnable)}：客户端事件本来就在主线程上触发，而
 * {@code BlockableEventLoop#execute} 在主线程时是<b>立即执行</b>的，根本延后不了。
 * 用一个跨 tick 的一次性标记才真正保证顺序——无论本体这一拍开了什么界面，
 * 我们的页都会在它<b>之后</b>设置，成为唯一可见的那一个；若取消确实生效、本体什么都没开，
 * 一拍后打开也没有任何副作用。</p>
 *
 * <p>延后开屏不会把「本体这一拍开的界面」当成 parent：{@link #pendingParent} 取的是<b>玩家打开前</b>的
 * {@code minecraft.screen}（通常是 {@code null} 或玩家自己原来那页）。另用 {@link #pendingKey}
 * 挡住同一次右键里因主手/副手、预测重放而重复排队的开屏，保证一次右键最多开一页。</p>
 *
 * <p>页面上显示的数值全部来自 {@link ManualLinkClientState}（服务端同步的只读镜像），
 * 这里不缓存任何东西。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID, value = Dist.CLIENT)
public final class PoleConfigClientEvents {
    /**
     * 已记下、但还没到下一个客户端 tick 的待开页面。{@code null} = 没有待办。
     *
     * <p>右键事件每只手各触发一次，NeoForge 还会为预测再走一遍；没有这个闸门时，
     * 同一个 tick 里会记下多次开屏，虽然最终只显示最后一个，但会重复构造页面。
     * 真正开屏时立刻清掉，之后玩家再按又是新的一次。</p>
     */
    private static @Nullable PoleKey pendingKey;
    /** 玩家按下热键前的界面，作为我们页面的 parent；不能取本体这一拍可能刚开的界面。 */
    private static @Nullable Screen pendingParent;

    private PoleConfigClientEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (!AddonKeyMappings.POLE_CONFIG.get().isDown()) return;

        // ★ 这个事件**每只手各触发一次**（主手、副手），NeoForge 还会为预测再走一遍。
        // 不挡掉第二次的后果不是「多开一次」那么简单：第二次会把**第一次开的屏幕**当成 parent
        // 再开一层。只认主手即可，一次右键只开一页。
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (Minecraft.getInstance().screen != null) return;
        if (pendingKey != null) return;

        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof AnvilHammerItem)
            && !(player.getOffhandItem().getItem() instanceof AnvilHammerItem)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        // 认杆只有一条路（PoleKey / PoleResolver），不在这里再写一遍方块清单。
        BlockPos top = PoleResolver.resolveTop(event.getLevel(), event.getPos());
        if (top == null) return;

        // ★ parent 必须在延后之前取：此时本体还没机会把它的 AnvilHammerScreen 塞进 minecraft.screen。
        pendingParent = minecraft.screen;
        pendingKey = PoleKey.of(event.getLevel(), top);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * 下一个客户端 tick 才真正开页：这时本体那一拍（若有）开的界面已经设置完，
     * 我们后设置，于是只剩我们的页可见。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        PoleKey key = pendingKey;
        if (key == null) return;

        Screen parent = pendingParent;
        pendingKey = null;
        pendingParent = null;

        Minecraft minecraft = Minecraft.getInstance();
        // 排队期间可能已经断线/切世界；那时不应再把一个旧 key 的页面盖上去。
        if (minecraft.level == null || minecraft.player == null) return;
        minecraft.setScreen(new PoleReconnectSortScreen(key, parent));
    }
}
