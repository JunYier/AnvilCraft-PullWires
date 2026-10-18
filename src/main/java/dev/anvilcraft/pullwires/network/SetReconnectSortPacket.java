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
 * 客户端 → 服务端：写入某根输电杆的<b>重连申请排序</b>设定。
 *
 * <p>与 {@link AdjustPriorityPacket} 同一套路：客户端只传「哪些开关被按了」这种原始意图，
 * 因素与方向的组合由服务端用 {@code ReconnectPolicy.ofFlags} 拼出来、落在权威的 {@code PoleConfig} 上。
 * 客户端不参与「最终是什么策略」的计算。</p>
 *
 * <p>V1.12.13 起没有 {@code override} 字段：写入的就是<b>这根杆自己</b>的设定，
 * 「覆盖 / 跟随默认」这个概念已经整体删除。</p>
 *
 * @param pos                点击的任意一段坐标；服务端自行解析到顶段
 * @param demandFirst        主因素是不是「需求」
 * @param primaryAscending   主因素是否升序
 * @param secondaryAscending 副因素是否升序
 */
public record SetReconnectSortPacket(
    BlockPos pos,
    boolean demandFirst,
    boolean primaryAscending,
    boolean secondaryAscending
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetReconnectSortPacket> TYPE =
        new CustomPacketPayload.Type<>(AnvilCraftPullWires.of("set_reconnect_sort"));

    public static final StreamCodec<ByteBuf, SetReconnectSortPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetReconnectSortPacket::pos,
        ByteBufCodecs.BOOL, SetReconnectSortPacket::demandFirst,
        ByteBufCodecs.BOOL, SetReconnectSortPacket::primaryAscending,
        ByteBufCodecs.BOOL, SetReconnectSortPacket::secondaryAscending,
        SetReconnectSortPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetReconnectSortPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PoleHammerBehavior.setReconnectSort(
                    player,
                    payload.pos(),
                    payload.demandFirst(),
                    payload.primaryAscending(),
                    payload.secondaryAscending()
                );
            }
        });
    }
}
