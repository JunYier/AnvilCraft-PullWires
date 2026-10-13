package dev.anvilcraft.pullwires.network;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.client.ManualLinkClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：同步当前玩家的待连接主端（没有则为空）。
 *
 * <p>待连接状态是服务端权威的——服务端可能因为距离、死亡、成功建立等原因把它清掉，
 * 客户端必须跟着清，否则粒子束会一直挂在那里。</p>
 */
public record SyncPendingLinkPacket(BlockPos master) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPendingLinkPacket> TYPE =
        new CustomPacketPayload.Type<>(AnvilCraftPullWires.of("sync_pending_link"));

    public static final StreamCodec<ByteBuf, SyncPendingLinkPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBoolean(packet.master() != null);
            if (packet.master() != null) BlockPos.STREAM_CODEC.encode(buffer, packet.master());
        },
        buffer -> new SyncPendingLinkPacket(buffer.readBoolean() ? BlockPos.STREAM_CODEC.decode(buffer) : null)
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncPendingLinkPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> ManualLinkClientState.setPendingMaster(payload.master()));
    }
}
