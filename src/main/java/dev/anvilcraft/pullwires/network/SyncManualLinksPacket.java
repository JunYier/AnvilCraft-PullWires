package dev.anvilcraft.pullwires.network;

import com.mojang.serialization.Codec;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 服务端 → 客户端：同步全部链路定义。
 *
 * <p>客户端需要它来做两件事：把手动连接的电力线画出来（本体只会按范围自动连线，手动画不出来），
 * 以及将来做链路相关的界面。用 NBT + {@link LinkDefinition#CODEC} 承载，
 * 与本体 {@code SimplePowerGrid} 的同步方式保持一致。</p>
 */
public record SyncManualLinksPacket(CompoundTag data) implements CustomPacketPayload {
    private static final Codec<List<LinkDefinition>> LIST_CODEC = LinkDefinition.CODEC.listOf();
    private static final String LINKS_KEY = "links";

    public static final CustomPacketPayload.Type<SyncManualLinksPacket> TYPE =
        new CustomPacketPayload.Type<>(AnvilCraftPullWires.of("sync_manual_links"));

    public static final StreamCodec<ByteBuf, SyncManualLinksPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeNbt(packet.data()),
        buffer -> new SyncManualLinksPacket(Objects.requireNonNullElseGet(buffer.readNbt(), CompoundTag::new))
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncManualLinksPacket of(Collection<LinkDefinition> links) {
        CompoundTag tag = new CompoundTag();
        tag.put(LINKS_KEY, LIST_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(links)).getOrThrow());
        return new SyncManualLinksPacket(tag);
    }

    public List<LinkDefinition> links() {
        Tag tag = this.data.get(LINKS_KEY);
        if (tag == null) return List.of();
        return LIST_CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(List.of());
    }

    public static void handle(SyncManualLinksPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.anvilcraft.pullwires.client.ManualLinkClientState.setLinks(payload.links()));
    }
}
