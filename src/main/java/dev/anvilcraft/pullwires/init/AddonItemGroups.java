package dev.anvilcraft.pullwires.init;

import static dev.anvilcraft.pullwires.AnvilCraftPullWires.REGISTRUM;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.dubhe.anvilcraft.init.item.ModItemGroups;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AddonItemGroups {
        private static final DeferredRegister<CreativeModeTab> DEFERRED_REGISTER = DeferredRegister.create(
                        Registries.CREATIVE_MODE_TAB,
                        AnvilCraftPullWires.MOD_ID);

        public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ADDON_ITEMS = DEFERRED_REGISTER.register(
                        "addon_items",
                        () -> CreativeModeTab.builder()
                                        .icon(AddonItems.EXAMPLE_ITEM::asStack)
                                        .displayItems((ctx, entries) -> {
                                        })
                                        .title(
                                                        REGISTRUM.addLang(
                                                                        "itemGroup",
                                                                        AnvilCraftPullWires.of("addon_items"),
                                                                        "AnvilCraft: Addon Template"))
                                        .withTabsBefore(ModItemGroups.ANVILCRAFT_BUILD_BLOCK.getId())
                                        .build());

        public static void register(IEventBus modEventBus) {
                DEFERRED_REGISTER.register(modEventBus);
        }
}
