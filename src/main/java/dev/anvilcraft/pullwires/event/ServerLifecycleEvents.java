package dev.anvilcraft.pullwires.event;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.PowerInterconnectManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID)
public class ServerLifecycleEvents {
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // 结算缓存里存着 PowerGrid 实例，跨存档必须清掉，否则会拦住旧世界的电网对象。
        PowerInterconnectManager.clear();
    }
}
