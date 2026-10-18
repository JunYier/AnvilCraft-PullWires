package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 铁砧锤 + 热键（默认左 Ctrl）<b>右键</b>电线杆 → 打开那根杆专属的重连排序配置页。
 *
 * <h2>为什么必须在客户端最高优先级上拦下这次右键</h2>
 * <p>本体的铁砧锤走 {@code client/event/ClientBlockEventListener}（优先级 {@code HIGH}）：
 * 它在客户端命中方块后<b>主动发一个 {@code HammerUsePacket}</b>，服务端收到后调用
 * {@code AnvilHammerItem.useBlock} → {@code PoleHammerBehavior.change} <b>切换锁定</b>。
 * 也就是说这次右键的语义完全由客户端的那个监听器决定，服务端并不知道玩家按没按热键。</p>
 * <p>而 {@code PlayerInteractEvent.RightClickBlock} 在客户端也会触发，且<b>被取消后不会再发给
 * 优先级更低的监听器</b>（NeoForge 默认不向监听器投递已取消的事件）。因此只要这里用
 * {@code HIGHEST} 抢先取消，本体那条「发 HammerUsePacket」的路径根本不会执行，
 * 这一下右键<b>既不会切锁定、也不会在本体那边产生任何副作用</b>——配置页与「右键切锁定」
 * 因此可以共用同一个按键而互不干扰。</p>
 *
 * <p>页面上显示的数值全部来自 {@link ManualLinkClientState}（服务端同步的只读镜像），
 * 这里不缓存任何东西。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID, value = Dist.CLIENT)
public final class PoleConfigClientEvents {
    private PoleConfigClientEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (!AddonKeyMappings.POLE_CONFIG.get().isDown()) return;

        // ★ 这个事件**每只手各触发一次**（主手、副手），NeoForge 还会为预测再走一遍。
        // 不挡掉第二次的后果不是「多开一次」那么简单：第二次会把**第一次开的屏幕**当成 parent
        // 再开一层（`new PoleReconnectSortScreen(key, minecraft.screen)`），于是两个界面叠在一起，
        // 关掉上面那层还会露出下面那层。只认主手即可，一次右键只开一页。
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (Minecraft.getInstance().screen != null) return;

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

        minecraft.setScreen(new PoleReconnectSortScreen(
            PoleKey.of(event.getLevel(), top),
            minecraft.screen
        ));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
