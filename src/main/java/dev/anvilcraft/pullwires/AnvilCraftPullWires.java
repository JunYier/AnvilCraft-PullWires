package dev.anvilcraft.pullwires;

import com.mojang.logging.LogUtils;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.anvilcraft.lib.v2.registrum.Registrum;
import dev.anvilcraft.pullwires.command.AddonCommands;
import dev.anvilcraft.pullwires.data.AddonDatagen;
import dev.anvilcraft.pullwires.init.AddonBlockEntities;
import dev.anvilcraft.pullwires.init.AddonItemGroups;
import dev.anvilcraft.pullwires.init.AddonBlocks;
import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.power.link.PoleHammerBehavior;
import dev.anvilcraft.pullwires.power.link.PowerLinkConnectivityChecker;
import dev.dubhe.anvilcraft.api.hammer.HammerManager;
import dev.dubhe.anvilcraft.api.power.ConnectivityChecker;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(AnvilCraftPullWires.MOD_ID)
public class AnvilCraftPullWires {
    public static final String MOD_ID = "anvilcraft_pullwires";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final AddonConfig CONFIG = ConfigManager.register(AnvilCraftPullWires.MOD_ID, AddonConfig::new);
    public static final Registrum REGISTRUM = Registrum.create(MOD_ID);

    public AnvilCraftPullWires(IEventBus modEventBus, ModContainer modContainer) {
        // 三段范围判定必须在任何电网操作之前注册；它能覆盖本体对输电杆的既有判定。
        ConnectivityChecker.register(PowerLinkConnectivityChecker.INSTANCE);
        NeoForge.EVENT_BUS.addListener(AddonCommands::register);
        modEventBus.addListener(AddonNetwork::register);
        // 铁砧锤右键输电杆 → 切换锁定。必须在本体 HammerManager.register()（服务器启动时）之前登记。
        // 四种杆都要登记：漏掉自己那两种的话，超远程杆与跨维度杆用铁砧锤右键会毫无反应。
        HammerManager.registerChange(() -> ModBlocks.TRANSMISSION_POLE.get(), PoleHammerBehavior.INSTANCE);
        HammerManager.registerChange(() -> ModBlocks.REMOTE_TRANSMISSION_POLE.get(), PoleHammerBehavior.INSTANCE);
        HammerManager.registerChange(() -> AddonBlocks.ULTRA_LONG_RANGE_POLE.get(), PoleHammerBehavior.INSTANCE);
        HammerManager.registerChange(() -> AddonBlocks.CROSS_DIMENSIONAL_POLE.get(), PoleHammerBehavior.INSTANCE);
        AddonItemGroups.register(modEventBus);
        AddonBlocks.register();
        AddonBlockEntities.register();
        AddonDatagen.init();
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
