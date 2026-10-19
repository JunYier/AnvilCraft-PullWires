package dev.anvilcraft.pullwires.block.entity;

import dev.anvilcraft.pullwires.init.AddonBlocks;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 超远程输电杆的方块实体：与普通杆唯一差别是范围取值（供电几乎为零、连接极远）。
 *
 * <p>grid 访问器、{@code getComponentType} 的顶段校验、进网/退网与状态同步都在
 * {@link AbstractAddonPoleBlockEntity} 里，这里只声明「自己是哪根杆」。</p>
 */
public class UltraLongRangePoleBlockEntity extends AbstractAddonPoleBlockEntity {
    public UltraLongRangePoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static UltraLongRangePoleBlockEntity createBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        return new UltraLongRangePoleBlockEntity(type, pos, state);
    }

    @Override
    protected Block ownBlock() {
        return AddonBlocks.ULTRA_LONG_RANGE_POLE.get();
    }

    @Override
    protected PoleKind kind() {
        return PoleKind.ULTRA_LONG_RANGE;
    }
}
