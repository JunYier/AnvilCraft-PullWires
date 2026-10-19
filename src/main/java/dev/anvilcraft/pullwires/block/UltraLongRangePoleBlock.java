package dev.anvilcraft.pullwires.block;

import dev.anvilcraft.pullwires.block.entity.UltraLongRangePoleBlockEntity;
import dev.anvilcraft.pullwires.init.AddonBlockEntities;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 超远程输电杆方块。
 *
 * <p>复用本体远程输电桩的四段多部分结构，只换方块实体。</p>
 *
 * <p><b>getTicker 必须一起重写</b>：本体那份会把方块实体强转成本体的类型，
 * 我们的方块实体不是它的子类，不重写就会在第一个 tick 抛 ClassCastException。</p>
 */
public class UltraLongRangePoleBlock extends RemoteTransmissionPoleBlock {
    /**
     * 顶段的碰撞箱，按视觉模型 {@code ultra_long_range_pole_head} 的几何取。
     *
     * <p>本体给远程输电桩顶段的形状是「1,11→15,13 的圆盘 + 4,0→12,16 的立柱」，
     * 而我们的头是另一套几何：底块 8×8、立柱 6×6、中部一圈 10×10、头部 6×6 到 y=13.25。
     * 形状必须跟着视觉走，否则会出现「看着穿过去了却撞在空气上」。</p>
     *
     * <p>其余三段（底段 / 中间两段）继续用本体的素材与形状，本方法只换顶段。</p>
     */
    private static final VoxelShape HEAD_SHAPE = Shapes.or(
        Block.box(4, 0, 4, 12, 2, 12),
        Block.box(5, 2, 5, 11, 4, 11),
        Block.box(3, 4, 3, 13, 6, 13),
        Block.box(5, 6, 5, 11, 13.25, 11)
    );

    public UltraLongRangePoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getPartShape(BlockState state) {
        return state.getValue(HALF) == Vertical4PartHalf.TOP ? HEAD_SHAPE : super.getPartShape(state);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UltraLongRangePoleBlockEntity(AddonBlockEntities.ULTRA_LONG_RANGE_POLE.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof UltraLongRangePoleBlockEntity pole) pole.tick(tickLevel, pos);
        };
    }
}