package dev.anvilcraft.pullwires.init;

import static dev.anvilcraft.pullwires.AnvilCraftPullWires.REGISTRUM;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;

public class AddonBlocks {
    static {
        REGISTRUM.defaultCreativeTab(AddonItemGroups.ADDON_ITEMS.getKey());
    }

    public static final BlockEntry<Block> EXAMPLE_BLOCK = REGISTRUM
            .block("example_block", Block::new)
            .simpleItem()
            .register();

    public static void register() {
    }
}
