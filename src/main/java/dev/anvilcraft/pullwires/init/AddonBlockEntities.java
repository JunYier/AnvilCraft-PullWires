package dev.anvilcraft.pullwires.init;

import static dev.anvilcraft.pullwires.AnvilCraftPullWires.REGISTRUM;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntityEntry;
import dev.anvilcraft.pullwires.block.entity.CrossDimensionalPoleBlockEntity;
import dev.anvilcraft.pullwires.block.entity.UltraLongRangePoleBlockEntity;

public class AddonBlockEntities {
    public static final BlockEntityEntry<UltraLongRangePoleBlockEntity> ULTRA_LONG_RANGE_POLE =
        REGISTRUM.blockEntity("ultra_long_range_pole", UltraLongRangePoleBlockEntity::createBlockEntity)
            .validBlock(AddonBlocks.ULTRA_LONG_RANGE_POLE)
            .register();

    public static final BlockEntityEntry<CrossDimensionalPoleBlockEntity> CROSS_DIMENSIONAL_POLE =
        REGISTRUM.blockEntity("cross_dimensional_pole", CrossDimensionalPoleBlockEntity::createBlockEntity)
            .validBlock(AddonBlocks.CROSS_DIMENSIONAL_POLE)
            .register();

    public static void register() {
        // 静态初始化即注册。
    }
}