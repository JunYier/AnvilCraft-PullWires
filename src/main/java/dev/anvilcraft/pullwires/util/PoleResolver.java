package dev.anvilcraft.pullwires.util;

import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.TransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.state.Vertical3PartHalf;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 把输电杆的任意一段解析成<b>顶段</b>坐标。
 *
 * <p>输电杆只有 {@code HALF == TOP} 那一段的方块实体才是中继元件
 * （其余段 {@code getComponentType()} 返回 {@code INVALID}），
 * 而链路的 {@code PoleKey} 必须以元件坐标登记，否则结算时按坐标匹配电网元件会找不到它。</p>
 */
public final class PoleResolver {
    private PoleResolver() {
    }

    public static @Nullable BlockPos resolveTop(BlockGetter level, BlockPos pos) {
        for (int dy = -3; dy <= 3; dy++) {
            BlockPos candidate = pos.offset(0, dy, 0);
            BlockState state = level.getBlockState(candidate);
            if (state.is(ModBlocks.TRANSMISSION_POLE.get())
                && state.getValue(TransmissionPoleBlock.HALF) == Vertical3PartHalf.TOP) {
                return candidate;
            }
            if (state.is(ModBlocks.REMOTE_TRANSMISSION_POLE.get())
                && state.getValue(RemoteTransmissionPoleBlock.HALF) == Vertical4PartHalf.TOP) {
                return candidate;
            }
        }
        return null;
    }

    /** 该坐标是否已经是某根输电杆的顶段。 */
    public static boolean isPoleTop(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.TRANSMISSION_POLE.get())) {
            return state.getValue(TransmissionPoleBlock.HALF) == Vertical3PartHalf.TOP;
        }
        if (state.is(ModBlocks.REMOTE_TRANSMISSION_POLE.get())) {
            return state.getValue(RemoteTransmissionPoleBlock.HALF) == Vertical4PartHalf.TOP;
        }
        return false;
    }
}
