package dev.anvilcraft.pullwires.network;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.ManualLinkSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：清除待连接状态（手持输电杆按住热键右键空气）。
 *
 * <p>这是唯一由玩家主动发起的清除方式。其余清除条件（死亡、退出世界、成功建立、超出放弃距离）
 * 都由服务端自己判定。</p>
 */
public record CancelManualLinkPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CancelManualLinkPacket> TYPE =
        new CustomPacketPayload.Type<>(AnvilCraftPullWires.of("cancel_manual_link"));

    public static final StreamCodec<ByteBuf, CancelManualLinkPacket> STREAM_CODEC =
        StreamCodec.unit(new CancelManualLinkPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CancelManualLinkPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ManualLinkSessions.cancel(player, true);
            }
        });
    }
}
