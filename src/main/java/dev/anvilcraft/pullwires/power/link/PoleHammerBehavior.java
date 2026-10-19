package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.util.PoleResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 铁砧锤在输电杆上的交互：右键切换锁定，Ctrl + 右键打开该杆的重连排序配置页。
 *
 * <p>本体自己就为「铁砧锤右键改方块」提供了扩展点 {@code HammerManager}，
 * 所以这里不需要 Mixin：把行为注册给输电杆方块即可，{@code AnvilHammerItem} 会在
 * 常规交互都失败后落到 {@code HammerManager.getChange(...).change(...)}。</p>
 *
 * <h2>为什么锁定不是「冻结优先级」</h2>
 * <p>需求把锁定定义为<b>忽略外部红石信号造成的 {@code SWITCH} 切换</b>。
 * 也就是说锁定只影响红石，优先级任何时候都能改——两者互不干扰。</p>
 *
 * <h2>优先级从哪改（V1.12.14 起）</h2>
 * <p>原来的「按住 Ctrl 滚动滚轮」已经删除，优先级现在只存在于配置页的 0–15 滑条里：
 * 拖动不写、松手才发一次增量。服务端的写入路径仍是这里的 {@link #adjustPriority}，
 * 因此「数值一律由服务端在权威数据上夹取」这条性质没有变。</p>
 */
public class PoleHammerBehavior implements dev.dubhe.anvilcraft.api.hammer.IHammerChangeable {
    public static final PoleHammerBehavior INSTANCE = new PoleHammerBehavior();

    private PoleHammerBehavior() {
    }

    // ------------------------------------------------------------------ 配置页滑条改优先级

    /**
     * 调整某根杆的优先级。
     *
     * @param clicked 玩家瞄准的任意一段坐标
     * @param delta   增量（滑条目标值减客户端镜像值），正数提高优先级
     */
    public static void adjustPriority(ServerPlayer player, BlockPos clicked, int delta) {
        if (delta == 0) return;
        BlockPos top = PoleResolver.resolveTop(player.level(), clicked);
        if (top == null) return;

        WireLinkRegistry registry = WireLinkRegistry.get(player.server);
        PoleKey key = PoleKey.of(player.level(), top);
        int previous = registry.config(key).priority();
        int updated = PolePriority.clamp(previous + delta);
        if (updated == previous) {
            player.displayClientMessage(
                message("priority.limit", Component.literal(String.valueOf(previous))).withStyle(ChatFormatting.GRAY),
                true
            );
            return;
        }
        registry.setPriority(key, updated);
        AddonNetwork.broadcastRegistry(player.server);
        // 不再打印「优先级 7 → 8」的动作条消息：目标杆的实时数值由铁砧锤 tooltip 承担，
        // 每滚一格刷一条消息只会淹没聊天栏。
    }

    // ------------------------------------------------------------------ 配置页：重连排序

    /**
     * 写入某根杆的重连申请排序设定（配置页上的三个按钮走这一条）。
     *
     * <p>与 {@link #adjustPriority} 同一个套路：客户端只说「按了哪个开关」，
     * 这里在权威数据上拼出策略并落进 {@code PoleConfig}，然后整体广播注册表——
     * 广播既是给别的玩家看的，也是给<b>这台客户端自己</b>看的：页面上的当前值直接读镜像，
     * 不另存一份可写状态。</p>
     *
     * <p>V1.12.13 起写的就是<b>这根杆自己</b>的设定，没有「要不要覆盖」这个入参：
     * 页面上按一按 = 直接落到本杆，存档级默认只在进世界补齐时作为一份快照被复制进来
     * （见 {@code WireLinkRegistry.backfillReconnectPolicies}）。</p>
     *
     * @param clicked            玩家点击的任意一段坐标
     * @param demandFirst        主因素是不是「需求」
     * @param primaryAscending   主因素是否升序
     * @param secondaryAscending 副因素是否升序
     */
    public static void setReconnectSort(
        ServerPlayer player,
        BlockPos clicked,
        boolean demandFirst,
        boolean primaryAscending,
        boolean secondaryAscending
    ) {
        BlockPos top = PoleResolver.resolveTop(player.level(), clicked);
        if (top == null) return;

        WireLinkRegistry registry = WireLinkRegistry.get(player.server);
        PoleKey key = PoleKey.of(player.level(), top);
        ReconnectPolicy policy = ReconnectPolicy.ofFlags(demandFirst, primaryAscending, secondaryAscending);
        registry.setReconnectPolicy(key, Optional.of(policy));
        AddonNetwork.broadcastRegistry(player.server);

        player.displayClientMessage(
            message("sort.saved").withStyle(ChatFormatting.GRAY),
            true
        );
    }

    // ------------------------------------------------------------------ 右键切锁定

    @Override
    public boolean change(Player player, BlockPos blockPos, Level level, ItemStack anvilHammer) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return false;
        BlockPos top = PoleResolver.resolveTop(level, blockPos);
        if (top == null) return false;

        WireLinkRegistry registry = WireLinkRegistry.get(serverPlayer.server);
        PoleKey key = PoleKey.of(level, top);
        boolean locked = !registry.config(key).locked();
        registry.setLocked(key, locked);
        if (!locked) {
            // 刚解锁：本体的 update 只在「邻居变化」时才会跑，而解锁本身不是一次邻居变化，
            // 所以这里主动触发一次——否则杆会停在锁定期间的旧开关状态，直到旁边有方块变动才被纠正。
            BlockPos bottom = PoleResolver.resolveBottom(level, blockPos);
            if (bottom != null) {
                BlockState bottomState = level.getBlockState(bottom);
                level.neighborChanged(bottom, bottomState.getBlock(), bottom);
            }
        }
        AddonNetwork.broadcastRegistry(serverPlayer.server);
        serverPlayer.displayClientMessage(
            message(locked ? "lock.locked" : "lock.unlocked").withStyle(
                locked ? ChatFormatting.GOLD : ChatFormatting.GRAY
            ),
            true
        );
        return true;
    }

    @Override
    public @Nullable Property<?> getChangeableProperty(BlockState blockState) {
        // 不做属性旋转，只借用「铁砧锤右键」这个入口。
        return null;
    }

    static MutableComponent message(String key, Object... args) {
        return Component.translatable("message.anvilcraft_pullwires." + key, args);
    }
}
