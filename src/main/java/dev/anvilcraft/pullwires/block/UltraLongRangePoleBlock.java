package dev.anvilcraft.pullwires.block;

import dev.anvilcraft.pullwires.block.entity.UltraLongRangePoleBlockEntity;
import dev.anvilcraft.pullwires.init.AddonBlockEntities;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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
    public UltraLongRangePoleBlock(Properties properties) {
        super(properties);
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