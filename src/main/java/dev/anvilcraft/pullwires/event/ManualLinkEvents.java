package dev.anvilcraft.pullwires.event;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.block.entity.CrossDimensionalPoleBlockEntity;
import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.power.link.LinkBinding;
import dev.anvilcraft.pullwires.power.link.ManualLinkSessions;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.power.link.WireLinkRegistry;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 手动连接的服务端事件入口。
 *
 * <p>覆盖需求里列明的四条被动清除条件：死亡、退出世界、成功建立（见 {@link #onPlace}），
 * 以及超出放弃距离（见 {@link #onPlayerTick}）。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID)
public class ManualLinkEvents {
    /** 逐玩家检查是否已离主端太远；只有处于待连接状态时才会真的做射线检测。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ManualLinkSessions.tick(player);
        }
    }

    /** 放下输电杆：若正处于待连接状态，按直线距离决定建立、拒绝还是放弃。 */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // 认杆只有一条路（PoleKind），不要在这里再写一遍方块清单：
        // 之前这里只认本体那两种杆，超远程杆与跨维度杆放下时根本不会触发连接。
        if (PoleKind.of(event.getPlacedBlock()) == null) return;
        // 绑定可以写在主手那根杆上，也可以写在副手的磁盘上——
        // 两条路都在 onPolePlaced 里取（见 LinkBinding#carrierOf）。
        ManualLinkSessions.onPolePlaced(player, level, event.getPos());
    }


    /**
     * 右键电线杆：绑定 / 粘贴 / 解除绑定。
     *
     * <p>用官方事件而不是自建 C2S 包——之前走 Ctrl 热键是因为热键是纯客户端状态、
     * 服务端看不到；改用普通右键后，这里能直接看到「点了哪一格、手上拿什么、是否按着 shift」。</p>
     *
     * <h2>磁盘的「复制 / 粘贴」</h2>
     * <p>磁盘与杆物品在这一下右键上<b>刻意不同</b>：</p>
     * <ul>
     *   <li><b>杆物品</b>：右键 = 把点击的杆记为新的主端。这是换主端的快捷方式，一次点完。</li>
     *   <li><b>磁盘</b>：空磁盘右键 = 记下主端（相当于本体磁盘的「复制」）；
     *       已经记着主端的磁盘右键 = <b>粘贴</b>，即当场把点击的杆连成从端，不再覆盖绑定。
     *       磁盘本来就是用来把主端「带走」的，走到对端却只能覆盖掉它，等于这一半功能是废的。</li>
     * </ul>
     * <p>想让磁盘回到「干净」状态、重新当普通磁盘用：shift 右键解除绑定即可。</p>
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            // 客户端：取消这次交互，消掉预测性放置（闪动）。
            //
            // 为什么取消是安全的——NeoForge 的 MultiPlayerGameMode#useItemOn 源码：
            //     startPrediction(level, seq -> { performUseItemOn(...);
            //                                   return new ServerboundUseItemOnPacket(...); });
            // 包由 startPrediction 独立发出，与交互结果无关；performUseItemOn 里的
            // `if (event.isCanceled()) return event.getCancellationResult();` 只影响返回值。
            //
            // 反例（不要用）：setUseItem(FALSE) 会让 performUseItemOn 走到
            //     `if (event.getUseItem().isFalse()) return InteractionResult.PASS;`
            // 返回 PASS 等于「没人处理」，原版反而会继续放置方块。
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        ServerLevel level = player.serverLevel();
        ItemStack stack = player.getItemInHand(event.getHand());
        // 这里必须覆盖**所有**可绑定物品（四种杆 + 磁盘），不能用物品的 useOn 代替：
        // 我们的杆继承自 SimpleMultiPartBlock，它重写了 useItemOn / useWithoutItem，
        // 方块会把右键吃掉，物品的 useOn 根本不会被调用。
        // 而本事件在方块交互之前触发，所以只有这条路能拿到右键。
        if (!LinkBinding.canBind(stack)) return;
        BlockPos top = PoleResolver.resolveTop(level, event.getPos());
        if (top == null) return;

        if (player.isShiftKeyDown()) {
            // 解绑。顺带把「磁盘回到普通磁盘」这条路留在这里。
            LinkBinding.clear(stack);
            player.displayClientMessage(
                Component.translatable("message.anvilcraft_pullwires.binding.cleared").withStyle(ChatFormatting.GRAY), true
            );
        } else if (stack.getItem() instanceof DiskItem && LinkBinding.read(stack) != null) {
            // 粘贴：磁盘已经记着主端，这一下是把点击的杆连成从端。
            ManualLinkSessions.onPolePasted(player, level, top, stack);
        } else {
            // 绑定时连同主端的杆种类一起记住：之后即使玩家跑到别的维度，
            // 也不必回头读主端那一格的方块状态就能判断距离规则与能否跨维度。
            LinkBinding.bind(stack, PoleKey.of(level, top), PoleKind.of(level.getBlockState(top)));
            player.displayClientMessage(boundMessage(stack), true);
        }
        event.setCanceled(true);
    }

    /** 绑定成功的提示；磁盘额外提示「再右键就是粘贴」。 */
    private static Component boundMessage(ItemStack stack) {
        MutableComponent message = Component.translatable("message.anvilcraft_pullwires.binding.bound")
            .withStyle(ChatFormatting.GOLD);
        if (stack.getItem() instanceof DiskItem) {
            message.append(Component.translatable("message.anvilcraft_pullwires.disk.paste_hint")
                .withStyle(ChatFormatting.GRAY));
        }
        return message;
    }



    /** 进服务器时推一份链路列表，否则客户端要等到下一次链路变化才知道该画哪些线。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AddonNetwork.sendRegistry(player);
        }
    }

    /**
     * shift + 右键（含右键空气）：解除绑定。
     *
     * <p>{@code RightClickBlock} 与物品的 {@code useOn} 都<b>只在打中方块时</b>触发，所以光靠它们，玩家必须瞄准一根杆才能解绑。这里补上「右键任意位置（含空气）」，让解绑不再需要瞄准。</p>
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!LinkBinding.canBind(stack) || LinkBinding.read(stack) == null) return;
        LinkBinding.clear(stack);
        player.displayClientMessage(
            Component.translatable("message.anvilcraft_pullwires.binding.cleared").withStyle(ChatFormatting.GRAY), true
        );
    }

    /**
     * 拆掉输电杆时回收它的链路。
     *
     * <p>不做这一步的话，链路定义会一直留在注册表里——电力线继续渲染、结算继续引用一个已经不存在的杆。</p>
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // 同样走 PoleKind：漏掉杆型的话，拆掉超远程杆或跨维度杆会留下指向空气的链路——
        // 线继续画、结算继续引用、HUD 继续列。
        if (PoleKind.of(event.getState()) == null) return;
        BlockPos top = PoleResolver.resolveTop(level, event.getPos());
        if (top == null) return;

        WireLinkRegistry registry = WireLinkRegistry.get(level.getServer());
        PoleKey key = PoleKey.of(level, top);
        boolean hadLinks = !registry.linksOf(key).isEmpty();
        registry.removePole(key);
        // 跨维度杆会强加载自己那一格，拆掉时释放。
        // 放在这里而不是方块实体的 setRemoved()：后者在服务器关停 / 区块卸载时也会触发，
        // 那时动 setChunkForced 会把「Saving worlds」卡死（见 CrossDimensionalPoleBlockEntity）。
        if (level.getBlockEntity(top) instanceof CrossDimensionalPoleBlockEntity pole) {
            pole.releaseChunkLoad();
        }
        if (hadLinks) AddonNetwork.broadcastRegistry(level.getServer());
    }
}
