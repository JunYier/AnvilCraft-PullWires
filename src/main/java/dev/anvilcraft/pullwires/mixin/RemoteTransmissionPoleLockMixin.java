package dev.anvilcraft.pullwires.mixin;

import dev.anvilcraft.pullwires.power.link.PoleRedstoneLock;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 远程输电桩的锁定拦截，逻辑与 {@link TransmissionPoleLockMixin} 完全一致，
 * 只是两个方块类各自持有一份同构的 {@code update}。
 */
@Mixin(RemoteTransmissionPoleBlock.class)
public abstract class RemoteTransmissionPoleLockMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private static void pullwires$ignoreRedstoneWhenLocked(
        BlockState state, Level level, BlockPos pos, CallbackInfo callback
    ) {
        if (PoleRedstoneLock.isLocked(level, pos)) callback.cancel();
    }
}
