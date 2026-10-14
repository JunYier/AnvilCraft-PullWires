package dev.anvilcraft.pullwires.power.link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 全网链路注册表。
 *
 * <p>以「维度 + 方块坐标」为键，集中存放本模组的全部持久数据：
 * 每根杆的 {@link PoleConfig}（优先级、锁定）与每条 {@link LinkDefinition}（手动连接、磁盘连接）。
 * 挂在主世界的 {@code DimensionDataStorage} 上，因此天然跨维度。</p>
 *
 * <p><b>为什么不用方块实体存</b>：本体的 {@code TransmissionPoleBlockEntity} 没有可扩展字段，
 * 而按坐标索引已足够——{@code PowerGrid.findPowerGridContains} 是公开 API，
 * 仅凭坐标就能解析出杆所属的电网，不需要读 BE 内部状态。</p>
 *
 * <p><b>锁定查询必须 O(1)</b>：M7 要在 {@code TransmissionPoleBlock#update} 里判定锁定，
 * 而该方法会在每次邻居变化时被调用，因此这里额外维护一个按维度的坐标集合作为快速索引。</p>
 */
public class WireLinkRegistry extends SavedData {
    private static final String DATA_NAME = "anvilcraft_pullwires_links";
    private static final String POLES_KEY = "poles";
    private static final String LINKS_KEY = "links";

    private static final SavedData.Factory<WireLinkRegistry> FACTORY = new SavedData.Factory<>(
        WireLinkRegistry::new,
        (tag, registries) -> {
            WireLinkRegistry registry = new WireLinkRegistry();
            registry.read(tag);
            return registry;
        }
    );

    /** 持久：每根杆的玩家配置。 */
    private final Map<PoleKey, PoleConfig> poleConfigs = new LinkedHashMap<>();

    /** 持久：链路定义。 */
    private final Map<UUID, LinkDefinition> links = new LinkedHashMap<>();

    /** 由 {@link #poleConfigs} 派生的锁定快速索引，不单独持久化。 */
    private final Map<ResourceKey<Level>, Set<BlockPos>> lockedIndex = new HashMap<>();

    /** 临时：每根杆的运行时值。 */
    private final Map<PoleKey, PoleRuntime> poleRuntimes = new HashMap<>();

    /** 临时：每条链路的状态。 */
    private final Map<UUID, LinkState> linkStates = new HashMap<>();

    public static WireLinkRegistry get(MinecraftServer server) {
        ServerLevel overworld = Objects.requireNonNull(server.getLevel(Level.OVERWORLD), "overworld");
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // ---------------------------------------------------------------- 杆配置

    /** 取该杆的配置；不存在时返回默认配置（不强写注册表，避免每次查询都产生一次改动）。 */
    public PoleConfig config(PoleKey key) {
        PoleConfig stored = this.poleConfigs.get(key);
        return stored != null ? stored : defaultConfig();
    }

    private static PoleConfig defaultConfig() {
        return new PoleConfig(AnvilCraftPullWires.CONFIG.powerLink.poleDefaultPriority, false);
    }

    public void setPriority(PoleKey key, int priority) {
        PoleConfig previous = this.config(key);
        PoleConfig updated = previous.withPriority(priority);
        if (updated.equals(previous)) return;
        this.poleConfigs.put(key, updated);
        this.setDirty();
    }

    public void setLocked(PoleKey key, boolean locked) {
        PoleConfig previous = this.config(key);
        if (previous.locked() == locked) return;
        this.poleConfigs.put(key, previous.withLocked(locked));
        if (locked) {
            this.lockedIndex.computeIfAbsent(key.dimension(), ignored -> new HashSet<>()).add(key.pos());
        } else {
            Set<BlockPos> dimensionLocked = this.lockedIndex.get(key.dimension());
            if (dimensionLocked != null) dimensionLocked.remove(key.pos());
        }
        this.setDirty();
    }

    /** 锁定快速索引查询：供 M7 的红石拦截在每次邻居变化时调用。 */
    public boolean isLocked(ResourceKey<Level> dimension, BlockPos pos) {
        Set<BlockPos> locked = this.lockedIndex.get(dimension);
        return locked != null && locked.contains(pos);
    }

    // ---------------------------------------------------------------- 运行时

    public PoleRuntime runtime(PoleKey key) {
        return this.poleRuntimes.computeIfAbsent(key, ignored -> new PoleRuntime());
    }

    /**
     * 记录该杆本 tick 的开关状态。
     *
     * @return 是否发生 OFF → ON 跃变（开关重启）
     */
    public boolean observeSwitch(PoleKey key, boolean switchOn) {
        return this.runtime(key).observeSwitch(switchOn);
    }

    /** 只清运行时计数器，不动链路定义与配置。 */
    public void resetRuntime(PoleKey key) {
        this.runtime(key).resetCounters();
    }

    // ---------------------------------------------------------------- 链路

    public UUID createLink(PoleKey master, PoleKey slave) {
        UUID id = UUID.randomUUID();
        this.links.put(id, new LinkDefinition(id, master, slave));
        this.linkStates.put(id, LinkState.ACTIVE);
        this.setDirty();
        return id;
    }

    public void removeLink(UUID id) {
        if (this.links.remove(id) == null) return;
        this.linkStates.remove(id);
        this.setDirty();
    }

    public @Nullable LinkDefinition link(UUID id) {
        return this.links.get(id);
    }

    public Collection<LinkDefinition> links() {
        return List.copyOf(this.links.values());
    }

    public List<LinkDefinition> linksOf(PoleKey key) {
        List<LinkDefinition> result = new ArrayList<>();
        for (LinkDefinition link : this.links.values()) {
            if (link.touches(key)) result.add(link);
        }
        return result;
    }

    /** 该杆作为主端管理的链路。 */
    public List<LinkDefinition> linksMasteredBy(PoleKey key) {
        List<LinkDefinition> result = new ArrayList<>();
        for (LinkDefinition link : this.links.values()) {
            if (link.master().equals(key)) result.add(link);
        }
        return result;
    }

    public LinkState stateOf(UUID id) {
        return this.linkStates.getOrDefault(id, LinkState.ACTIVE);
    }

    /** 状态是运行时值，不入存档，因此这里不需要标记脏。 */
    public void setState(UUID id, LinkState state) {
        this.linkStates.put(id, state);
    }

    /** 拆杆或方块被移除时清理：删掉该杆的全部配置、运行时值以及涉及它的链路。 */
    public void removePole(PoleKey key) {
        boolean dirty = this.poleConfigs.remove(key) != null;
        this.poleRuntimes.remove(key);
        Set<BlockPos> locked = this.lockedIndex.get(key.dimension());
        if (locked != null) locked.remove(key.pos());

        List<UUID> affected = new ArrayList<>();
        for (LinkDefinition link : this.links.values()) {
            if (link.touches(key)) affected.add(link.id());
        }
        for (UUID id : affected) {
            this.links.remove(id);
            this.linkStates.remove(id);
        }
        if (dirty || !affected.isEmpty()) this.setDirty();
    }

    // ---------------------------------------------------------------- 持久化

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        List<PoleEntry> poles = new ArrayList<>(this.poleConfigs.size());
        for (Map.Entry<PoleKey, PoleConfig> entry : this.poleConfigs.entrySet()) {
            poles.add(new PoleEntry(entry.getKey(), entry.getValue()));
        }
        tag.put(POLES_KEY, encode(PoleEntry.CODEC.listOf(), poles));
        tag.put(LINKS_KEY, encode(LinkDefinition.CODEC.listOf(), List.copyOf(this.links.values())));
        return tag;
    }

    public void read(CompoundTag tag) {
        this.poleConfigs.clear();
        this.links.clear();
        this.linkStates.clear();

        for (Tag element : tag.getList(POLES_KEY, Tag.TAG_COMPOUND)) {
            decodeLenient(PoleEntry.CODEC, element).ifPresent(entry -> this.poleConfigs.put(entry.key(), entry.config()));
        }
        for (Tag element : tag.getList(LINKS_KEY, Tag.TAG_COMPOUND)) {
            decodeLenient(LinkDefinition.CODEC, element)
                .ifPresent(link -> this.links.put(link.id(), link));
        }
        this.rebuildLockedIndex();
    }

    private void rebuildLockedIndex() {
        this.lockedIndex.clear();
        for (Map.Entry<PoleKey, PoleConfig> entry : this.poleConfigs.entrySet()) {
            if (!entry.getValue().locked()) continue;
            this.lockedIndex
                .computeIfAbsent(entry.getKey().dimension(), ignored -> new HashSet<>())
                .add(entry.getKey().pos());
        }
    }

    private static <T> Tag encode(Codec<T> codec, T value) {
        return codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    /** 宽容解析：单条数据损坏时跳过该条而不是让整个存档读取失败。 */
    private static <T> Optional<T> decodeLenient(Codec<T> codec, Tag tag) {
        return codec.parse(NbtOps.INSTANCE, tag).result();
    }

    /** 存档中的一根杆配置条目。对外公开是因为客户端同步也要用它。 */
    public record PoleEntry(PoleKey key, PoleConfig config) {
        public static final Codec<PoleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PoleKey.CODEC.fieldOf("key").forGetter(PoleEntry::key),
            PoleConfig.CODEC.fieldOf("config").forGetter(PoleEntry::config)
        ).apply(instance, PoleEntry::new));
    }

    /** 全部杆配置，供客户端同步使用。 */
    public List<PoleEntry> poleEntries() {
        List<PoleEntry> entries = new ArrayList<>(this.poleConfigs.size());
        for (Map.Entry<PoleKey, PoleConfig> entry : this.poleConfigs.entrySet()) {
            entries.add(new PoleEntry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }
}
