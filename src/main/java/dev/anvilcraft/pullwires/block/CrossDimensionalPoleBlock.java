package dev.anvilcraft.pullwires.block;

import dev.anvilcraft.pullwires.block.entity.CrossDimensionalPoleBlockEntity;
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
 * 跨维度输电杆方块。
 *
 * <p>复用本体远程输电桩的四段多部分结构，只换方块实体与顶段外观/碰撞箱；
 * ticker 与头部下段碰撞箱在 {@link AbstractAddonPoleBlock} 里共用。</p>
 */
public class CrossDimensionalPoleBlock extends AbstractAddonPoleBlock {
    /**
     * 顶段碰撞箱，按视觉模型 {@code cross_dimensional_pole_head} 取：下段公共部分 +
     * 自己的上半部分（{@code core_outline} 外壳 8×8×8，y8..16）。
     */
    private static final VoxelShape HEAD_SHAPE = Shapes.or(
        HEAD_LOWER_SHAPE,
        Block.box(4, 8, 4, 12, 16, 12)
    );

    public CrossDimensionalPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getPartShape(BlockState state) {
        return state.getValue(HALF) == Vertical4PartHalf.TOP ? HEAD_SHAPE : super.getPartShape(state);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrossDimensionalPoleBlockEntity(AddonBlockEntities.CROSS_DIMENSIONAL_POLE.get(), pos, state);
    }
}
