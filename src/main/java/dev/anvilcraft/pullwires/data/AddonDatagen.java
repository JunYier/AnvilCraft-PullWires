package dev.anvilcraft.pullwires.data;

import static dev.anvilcraft.pullwires.AnvilCraftPullWires.REGISTRUM;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.data.lang.AddonLangHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID)
public class AddonDatagen {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
    }

    /**
     * 初始化生成器
     */
    public static void init() {
        REGISTRUM.addDataGenerator(ProviderType.LANG, AddonLangHandler::init);
    }
}
