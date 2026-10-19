package dev.anvilcraft.pullwires.block;

import dev.anvilcraft.pullwires.block.entity.UltraLongRangePoleBlockEntity;
import dev.anvilcraft.pullwires.init.AddonBlockEntities;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 超远程输电杆方块。
 *
 * <p>复用本体远程输电桩的四段多部分结构，只换方块实体与顶段外观/碰撞箱；
 * ticker 与头部下段碰撞箱在 {@link AbstractAddonPoleBlock} 里共用。</p>
 */
public class UltraLongRangePoleBlock extends AbstractAddonPoleBlock {
    /**
     * 顶段碰撞箱，按视觉模型 {@code ultra_long_range_pole_head} 取：下段公共部分 +
     * 自己的上半部分（头部 6×6 到 y=13.25）。
     *
     * <p>其余三段（底段 / 中间两段）继续用本体的素材与形状，本方法只换顶段。</p>
     */
    private static final VoxelShape HEAD_SHAPE = Shapes.or(
        HEAD_LOWER_SHAPE,
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
}
