package dev.anvilcraft.pullwires.init;

import static dev.anvilcraft.pullwires.AnvilCraftPullWires.REGISTRUM;

import dev.anvilcraft.lib.v2.registrum.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

public class AddonItems {
    static {
        REGISTRUM.defaultCreativeTab(AddonItemGroups.ADDON_ITEMS.getKey());
    }

    public static final ItemEntry<Item> EXAMPLE_ITEM = REGISTRUM
            .item("example_item", Item::new)
            .register();

    public static void register() {
    }
}
