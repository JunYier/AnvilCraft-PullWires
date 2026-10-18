package dev.anvilcraft.pullwires.network;

import com.mojang.serialization.Codec;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.WireLinkRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 服务端 → 客户端：同步链路定义与杆配置。
 *
 * <p>客户端需要它来做两件事：</p>
 * <ul>
 *   <li>把手动连接的电力线画出来——本体只会按范围自动连线，手动画不出来；</li>
 *   <li>HUD 显示瞄准中的那根杆的实时优先级与锁定状态。</li>
 * </ul>
 *
 * <p>用 NBT + 现成的 Codec 承载，与本体 {@code SimplePowerGrid} 的同步方式保持一致。
 * 两者一起发是因为它们都是「注册表的客户端镜像」，分开会导致客户端出现半新半旧的状态。
 * 随包附带的 {@link WireLinkRegistry.Defaults} 同理：缺省值一变，客户端对「没有条目的那根杆」
 * 的显示就必须跟着变。</p>
 */
public record SyncRegistryPacket(CompoundTag data) implements CustomPacketPayload {
    private static final Codec<List<LinkDefinition>> LINKS_CODEC = LinkDefinition.CODEC.listOf();
    private static final Codec<List<WireLinkRegistry.PoleEntry>> POLES_CODEC = WireLinkRegistry.PoleEntry.CODEC.listOf();
    private static final String LINKS_KEY = "links";
    private static final String POLES_KEY = "poles";
    private static final String DEFAULTS_KEY = "defaults";

    public static final CustomPacketPayload.Type<SyncRegistryPacket> TYPE =
        new CustomPacketPayload.Type<>(AnvilCraftPullWires.of("sync_registry"));

    public static final StreamCodec<FriendlyByteBuf, SyncRegistryPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeNbt(packet.data()),
        buffer -> new SyncRegistryPacket(Objects.requireNonNullElseGet(buffer.readNbt(), CompoundTag::new))
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncRegistryPacket of(
        Collection<LinkDefinition> links,
        Collection<WireLinkRegistry.PoleEntry> poles,
        WireLinkRegistry.Defaults defaults
    ) {
        CompoundTag tag = new CompoundTag();
        tag.put(LINKS_KEY, LINKS_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(links)).getOrThrow());
        tag.put(POLES_KEY, POLES_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(poles)).getOrThrow());
        tag.put(DEFAULTS_KEY, WireLinkRegistry.Defaults.CODEC.encodeStart(NbtOps.INSTANCE, defaults).getOrThrow());
        return new SyncRegistryPacket(tag);
    }

    public List<LinkDefinition> links() {
        return decode(LINKS_CODEC, LINKS_KEY);
    }

    public List<WireLinkRegistry.PoleEntry> poles() {
        return decode(POLES_CODEC, POLES_KEY);
    }

    /**
     * 服务端的缺省值：杆配置缺省项 + 存档级默认排序。
     *
     * <p>客户端拿它回答「这根杆在存档里没有条目时该显示什么」。
     * 缺失（老服务端）时返回 {@code null}，由客户端保留自己那份内置缺省值。</p>
     */
    public @Nullable WireLinkRegistry.Defaults defaults() {
        Tag tag = this.data.get(DEFAULTS_KEY);
        if (tag == null) return null;
        return WireLinkRegistry.Defaults.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }

    private <T> List<T> decode(Codec<List<T>> codec, String key) {
        Tag tag = this.data.get(key);
        if (tag == null) return List.of();
        return codec.parse(NbtOps.INSTANCE, tag).result().orElse(List.of());
    }

    public static void handle(SyncRegistryPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.anvilcraft.pullwires.client.ManualLinkClientState.apply(payload));
    }
}
