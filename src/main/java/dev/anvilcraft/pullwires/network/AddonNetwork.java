package dev.anvilcraft.pullwires.network;

import dev.anvilcraft.pullwires.power.link.WireLinkRegistry;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AddonNetwork {
    private AddonNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // 版本号与本体保持一致的做法：协议不兼容时客户端会直接被拒绝连接。
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            AdjustPriorityPacket.TYPE,
            AdjustPriorityPacket.STREAM_CODEC,
            AdjustPriorityPacket::handle
        );
        registrar.playToClient(
            SyncLinkStatsPacket.TYPE,
            SyncLinkStatsPacket.STREAM_CODEC,
            SyncLinkStatsPacket::handle
        );
        registrar.playToClient(
            SyncRegistryPacket.TYPE,
            SyncRegistryPacket.STREAM_CODEC,
            SyncRegistryPacket::handle
        );
    }

    /** 注册表发生变化时广播；客户端要靠它画手动电力线、显示实时优先级。 */
    public static void broadcastRegistry(MinecraftServer server) {
        if (server == null) return;
        WireLinkRegistry registry = WireLinkRegistry.get(server);
        PacketDistributor.sendToAllPlayers(SyncRegistryPacket.of(registry.links(), registry.poleEntries()));
    }

    /** 每拍推送一次转移量统计。与注册表分开，避免每秒重发整份链路列表。 */
    public static void broadcastStats(
        MinecraftServer server,
        List<SyncLinkStatsPacket.Entry> entries,
        List<SyncLinkStatsPacket.StateEntry> states
    ) {
        if (server == null || (entries.isEmpty() && states.isEmpty())) return;
        PacketDistributor.sendToAllPlayers(SyncLinkStatsPacket.of(entries, states));
    }

    /** 玩家刚进服务器时单独推一份。 */
    public static void sendRegistry(ServerPlayer player) {
        WireLinkRegistry registry = WireLinkRegistry.get(player.server);
        PacketDistributor.sendToPlayer(player, SyncRegistryPacket.of(registry.links(), registry.poleEntries()));
    }
}
