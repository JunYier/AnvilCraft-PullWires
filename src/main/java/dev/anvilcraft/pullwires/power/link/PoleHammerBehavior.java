package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.util.PoleResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;

/**
 * 铁砧锤在输电杆上的交互：右键切换锁定，滚轮调整优先级。
 *
 * <p>本体自己就为「铁砧锤右键改方块」提供了扩展点 {@code HammerManager}，
 * 所以这里不需要 Mixin：把行为注册给输电杆方块即可，{@code AnvilHammerItem} 会在
 * 常规交互都失败后落到 {@code HammerManager.getChange(...).change(...)}。</p>
 *
 * <h2>为什么锁定不是「冻结优先级」</h2>
 * <p>需求把锁定定义为<b>忽略外部红石信号造成的 {@code SWITCH} 切换</b>。
 * 也就是说锁定只影响红石，优先级任何时候都能用滚轮改——两者互不干扰。</p>
 */
public class PoleHammerBehavior implements dev.dubhe.anvilcraft.api.hammer.IHammerChangeable {
    public static final PoleHammerBehavior INSTANCE = new PoleHammerBehavior();

    private PoleHammerBehavior() {
    }

    // ------------------------------------------------------------------ 滚轮改优先级

    /**
     * 调整某根杆的优先级。
     *
     * @param clicked 玩家瞄准的任意一段坐标
     * @param delta   滚轮方向，正数提高优先级
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
        player.displayClientMessage(
            message(
                "priority.changed",
                Component.literal(String.valueOf(previous)).withStyle(ChatFormatting.YELLOW),
                Component.literal(String.valueOf(updated)).withStyle(ChatFormatting.GREEN)
            ).withStyle(ChatFormatting.WHITE),
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
