package dev.anvilcraft.pullwires.mixin;

import dev.anvilcraft.pullwires.power.link.PoleRedstoneLock;
import dev.dubhe.anvilcraft.block.TransmissionPoleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 锁定后忽略红石造成的 {@code SWITCH} 切换。
 *
 * <p>本体的红石通路是：{@code neighborChanged} / {@code onPlace} → {@code update(state, level, pos)}
 * → 按 {@code level.hasNeighborSignal} 把底段与顶段的 {@code SWITCH} 一起置 {@code ON}/{@code OFF}。
 * 所以只要在 {@code update} 入口拦掉，红石就再也改不动该杆——既不会断电，也不会因为断电再上电
 * 而触发「开关重启」的链路恢复。</p>
 *
 * <p>{@code require = 0}：本体改动该方法时静默失效，退化为「锁定不起作用」，而不是让游戏启动失败。</p>
 */
@Mixin(TransmissionPoleBlock.class)
public abstract class TransmissionPoleLockMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private static void pullwires$ignoreRedstoneWhenLocked(
        BlockState state, Level level, BlockPos pos, CallbackInfo callback
    ) {
        if (PoleRedstoneLock.isLocked(level, pos)) callback.cancel();
    }
}
