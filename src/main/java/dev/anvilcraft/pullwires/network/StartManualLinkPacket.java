package dev.anvilcraft.pullwires.network;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.ManualLinkSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：请求进入手动连接的待连接状态。
 *
 * <p>热键是纯客户端状态，服务端看不到，所以必须由客户端在「按住热键右键了一根杆」时主动上报。
 * 服务端会重新校验手持物与坐标，不信任客户端传来的位置。</p>
 */
public record StartManualLinkPacket(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StartManualLinkPacket> TYPE =
        new CustomPacketPayload.Type<>(AnvilCraftPullWires.of("start_manual_link"));

    public static final StreamCodec<ByteBuf, StartManualLinkPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, StartManualLinkPacket::pos, StartManualLinkPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StartManualLinkPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ManualLinkSessions.start(player, payload.pos());
            }
        });
    }
}
