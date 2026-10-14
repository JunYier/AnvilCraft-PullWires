package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = AnvilCraftPullWires.MOD_ID, dist = Dist.CLIENT)
public class AnvilCraftPullWiresClient {
    public AnvilCraftPullWiresClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(AddonKeyMappings::register);
        modBus.addListener(ManualLinkHud::register);
    }
}
