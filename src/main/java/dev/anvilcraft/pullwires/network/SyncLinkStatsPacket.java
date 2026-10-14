package dev.anvilcraft.pullwires.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;
import java.util.Objects;

/**
 * 服务端 → 客户端：链路的转移量统计，每拍（1 秒）推送一次。
 *
 * <p>与 {@link SyncRegistryPacket} 分开是因为两者的<b>变化频率差了两个数量级</b>：
 * 链路与杆配置只在玩家操作时变，而转移量每拍都在变。混在一起会让每次结算都重发整份链路列表。</p>
 */
public record SyncLinkStatsPacket(CompoundTag data) implements CustomPacketPayload {
    private static final Codec<List<Entry>> ENTRIES_CODEC = Entry.CODEC.listOf();
    private static final String ENTRIES_KEY = "entries";
    private static final String STATES_KEY = "states";
    private static final Codec<List<StateEntry>> STATES_CODEC = StateEntry.CODEC.listOf();

    public static final CustomPacketPayload.Type<SyncLinkStatsPacket> TYPE =
        new CustomPacketPayload.Type<>(AnvilCraftPullWires.of("sync_link_stats"));

    public static final StreamCodec<FriendlyByteBuf, SyncLinkStatsPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeNbt(packet.data()),
        buffer -> new SyncLinkStatsPacket(Objects.requireNonNullElseGet(buffer.readNbt(), CompoundTag::new))
    );

    /**
     * 单根杆的统计。
     *
     * @param sendNow   本拍作为主端送出的电量
     * @param recvNow   本拍作为从端收到的电量
     * @param sendTotal 累计送出（持久化，自链路建立以来）
     * @param recvTotal 累计收到
     * @param gridOut   该杆所在电网本拍的净流出
     * @param gridIn    该杆所在电网本拍的净流入
     */
    public record Entry(
        PoleKey pole,
        int sendNow,
        int recvNow,
        long sendTotal,
        long recvTotal,
        int gridOut,
        int gridIn
    ) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PoleKey.CODEC.fieldOf("pole").forGetter(Entry::pole),
            Codec.INT.fieldOf("send_now").forGetter(Entry::sendNow),
            Codec.INT.fieldOf("recv_now").forGetter(Entry::recvNow),
            Codec.LONG.fieldOf("send_total").forGetter(Entry::sendTotal),
            Codec.LONG.fieldOf("recv_total").forGetter(Entry::recvTotal),
            Codec.INT.fieldOf("grid_out").forGetter(Entry::gridOut),
            Codec.INT.fieldOf("grid_in").forGetter(Entry::gridIn)
        ).apply(instance, Entry::new));
    }


    /** 单条链路的状态，客户端据此决定线色。 */
    public record StateEntry(UUID id, LinkState state) {
        public static final Codec<StateEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(StateEntry::id),
            Codec.STRING.xmap(LinkState::valueOf, LinkState::name).fieldOf("state").forGetter(StateEntry::state)
        ).apply(instance, StateEntry::new));
    }

    public static SyncLinkStatsPacket of(List<Entry> entries, List<StateEntry> states) {
        CompoundTag tag = new CompoundTag();
        tag.put(ENTRIES_KEY, ENTRIES_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(entries)).getOrThrow());
        tag.put(STATES_KEY, STATES_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(states)).getOrThrow());
        return new SyncLinkStatsPacket(tag);
    }

    /** 各链路的状态，线色渲染读它。 */
    public List<StateEntry> states() {
        Tag tag = this.data.get(STATES_KEY);
        if (tag == null) return List.of();
        return STATES_CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(List.of());
    }

    public List<Entry> entries() {
        Tag tag = this.data.get(ENTRIES_KEY);
        if (tag == null) return List.of();
        return ENTRIES_CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(List.of());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncLinkStatsPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.anvilcraft.pullwires.client.ManualLinkClientState.applyStats(payload));
    }
}
