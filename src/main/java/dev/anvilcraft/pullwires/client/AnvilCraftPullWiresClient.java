package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = AnvilCraftPullWires.MOD_ID, dist = Dist.CLIENT)
public class AnvilCraftPullWiresClient {
    /** 客户端配置。只在客户端注册，与本体 {@code AnvilCraft.CLIENT_CONFIG} 的做法一致。 */
    public static final AddonClientConfig CONFIG =
        ConfigManager.register(AnvilCraftPullWires.MOD_ID, AddonClientConfig::new);

    public AnvilCraftPullWiresClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(AddonKeyMappings::register);
        modBus.addListener(ConnectionListHud::register);
        modBus.addListener(PlacementHintHud::register);
        NeoForge.EVENT_BUS.addListener(AddonClientCommands::register);
    }
}
