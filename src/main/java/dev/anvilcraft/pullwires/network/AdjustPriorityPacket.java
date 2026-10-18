package dev.anvilcraft.pullwires.network;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.PoleHammerBehavior;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：配置页优先级滑条松手时调整输电杆的结算优先级。
 *
 * <p>只传<b>增量</b>（滑条目标值减客户端镜像值），最终数值由服务端在权威数据上夹取，
 * 客户端不参与计算。</p>
 */
public record AdjustPriorityPacket(BlockPos pos, int delta) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AdjustPriorityPacket> TYPE =
        new CustomPacketPayload.Type<>(AnvilCraftPullWires.of("adjust_priority"));

    public static final StreamCodec<ByteBuf, AdjustPriorityPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, AdjustPriorityPacket::pos,
        ByteBufCodecs.VAR_INT, AdjustPriorityPacket::delta,
        AdjustPriorityPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AdjustPriorityPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PoleHammerBehavior.adjustPriority(player, payload.pos(), payload.delta());
            }
        });
    }
}
