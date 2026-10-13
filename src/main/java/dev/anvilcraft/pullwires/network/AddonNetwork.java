package dev.anvilcraft.pullwires.network;

import dev.anvilcraft.pullwires.power.link.WireLinkRegistry;
import net.minecraft.server.MinecraftServer;
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
            StartManualLinkPacket.TYPE,
            StartManualLinkPacket.STREAM_CODEC,
            StartManualLinkPacket::handle
        );
        registrar.playToServer(
            CancelManualLinkPacket.TYPE,
            CancelManualLinkPacket.STREAM_CODEC,
            CancelManualLinkPacket::handle
        );
        registrar.playToClient(
            SyncPendingLinkPacket.TYPE,
            SyncPendingLinkPacket.STREAM_CODEC,
            SyncPendingLinkPacket::handle
        );
        registrar.playToClient(
            SyncManualLinksPacket.TYPE,
            SyncManualLinksPacket.STREAM_CODEC,
            SyncManualLinksPacket::handle
        );
    }

    /** 链路定义发生变化时广播给所有人；客户端要靠它把手动连接的电力线画出来。 */
    public static void broadcastLinks(MinecraftServer server) {
        if (server == null) return;
        PacketDistributor.sendToAllPlayers(SyncManualLinksPacket.of(WireLinkRegistry.get(server).links()));
    }

    /** 玩家刚进服务器时单独推一份。 */
    public static void sendLinks(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
            player,
            SyncManualLinksPacket.of(WireLinkRegistry.get(player.server).links())
        );
    }
}
