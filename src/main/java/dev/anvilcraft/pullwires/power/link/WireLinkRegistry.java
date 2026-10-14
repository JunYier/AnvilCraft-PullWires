package dev.anvilcraft.pullwires.power.link;

import com.mojang.serialization.Codec;
import dev.anvilcraft.pullwires.network.SyncLinkStatsPacket;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
import java.util.LinkedHashSet;
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
    private static final String LINK_SEQUENCE_KEY = "link_sequence";
    private static final String LINK_TOTALS_KEY = "link_totals";
    private static final String LINK_TOTAL_IDS_KEY = "link_total_ids";
    private static final Codec<List<Long>> LINK_TOTAL_CODEC = Codec.LONG.listOf();
    private static final Codec<List<UUID>> LINK_TOTAL_ID_CODEC = UUIDUtil.CODEC.listOf();
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

    /** 持久：每条链路的累计转移电量，用于 tooltip 的「总转移电量」。 */
    private final Map<UUID, Long> linkTotals = new LinkedHashMap<>();

    /** 临时：每根杆的统计快照，每拍重建，随同步包推给客户端。 */
    private final Map<PoleKey, SyncLinkStatsPacket.Entry> poleStats = new HashMap<>();

    /** 持久：链路建立序号的单调计数器。 */
    private long linkSequence;

    /** 临时：每条链路的净流出窗口与重连冷却。 */
    private final Map<UUID, LinkRuntime> linkRuntimes = new HashMap<>();

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
        return new PoleConfig(AnvilCraftPullWires.CONFIG.settlement.poleDefaultPriority, false);
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

    /**
     * 建立一条有向链路 {@code master -> slave}。
     *
     * <p>同向重复被拒绝（返回 {@code null}）；<b>反向允许</b>——{@code A -> B} 与 {@code B -> A}
     * 是两条独立的链路，这是 V1.4.1 允许「互为主从」的基础。自环始终禁止。</p>
     *
     * <p>建立序号取自一个持久化的单调计数器，而不是 {@code gameTime}：
     * 后者会被 {@code /time set} 改动，导致「相同优先级按建立时间排序」的结果被玩家随意摆布。</p>
     *
     * @return 新链路的 UUID；同向重复或自环时返回 {@code null}
     */
    public @Nullable UUID createLink(PoleKey master, PoleKey slave) {
        if (master.equals(slave)) return null;
        for (LinkDefinition existing : this.links.values()) {
            if (existing.master().equals(master) && existing.slave().equals(slave)) return null;
        }
        UUID id = UUID.randomUUID();
        this.links.put(id, new LinkDefinition(id, master, slave, ++this.linkSequence));
        this.linkStates.put(id, LinkState.ACTIVE);
        this.setDirty();
        return id;
    }

    public void removeLink(UUID id) {
        if (this.links.remove(id) == null) return;
        this.linkStates.remove(id);
        this.linkRuntimes.remove(id);
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

    /** 该链路的运行期数据（净流出窗口、重连冷却），按需创建。 */
    public LinkRuntime linkRuntime(UUID id) {
        return this.linkRuntimes.computeIfAbsent(id, ignored -> new LinkRuntime());
    }

    /** 全部链路的端点集合（去重）。开关跃变只需要观察这些杆。 */
    public Set<PoleKey> linkedPoles() {
        Set<PoleKey> result = new LinkedHashSet<>();
        for (LinkDefinition link : this.links.values()) {
            result.add(link.master());
            result.add(link.slave());
        }
        return result;
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
            this.linkRuntimes.remove(id);
            this.linkTotals.remove(id);
        }
        if (dirty || !affected.isEmpty()) this.setDirty();
    }

    /** 累加一条链路的转移量。累计值持久化，因此 tooltip 的「总转移电量」能跨会话保留。 */
    public void addLinkTotal(UUID id, long amount) {
        this.linkTotals.merge(id, amount, Long::sum);
        this.setDirty();
    }

    public long linkTotal(UUID id) {
        return this.linkTotals.getOrDefault(id, 0L);
    }

    /** 每拍由调度器整体替换一次，客户端 tooltip 读它。 */
    public void setPoleStats(List<SyncLinkStatsPacket.Entry> entries) {
        this.poleStats.clear();
        for (SyncLinkStatsPacket.Entry entry : entries) {
            this.poleStats.put(entry.pole(), entry);
        }
    }

    public SyncLinkStatsPacket.Entry poleStat(PoleKey key) {
        return this.poleStats.get(key);
    }

    public List<SyncLinkStatsPacket.Entry> poleStats() {
        return List.copyOf(this.poleStats.values());
    }

    private static <T> List<T> readTaggedList(Codec<List<T>> codec, CompoundTag tag, String key) {
        Tag element = tag.get(key);
        if (element == null) return List.of();
        return decodeLenient(codec, element).orElse(List.of());
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
        tag.putLong(LINK_SEQUENCE_KEY, this.linkSequence);
        tag.put(LINK_TOTALS_KEY, encode(LINK_TOTAL_CODEC, List.copyOf(this.linkTotals.values())));
        tag.put(LINK_TOTAL_IDS_KEY, encode(LINK_TOTAL_ID_CODEC, List.copyOf(this.linkTotals.keySet())));
        return tag;
    }

    public void read(CompoundTag tag) {
        this.poleConfigs.clear();
        this.links.clear();
        this.linkStates.clear();
        this.linkRuntimes.clear();
        this.linkTotals.clear();
        this.poleStats.clear();
        this.linkSequence = tag.getLong(LINK_SEQUENCE_KEY);
        List<Long> totals = readTaggedList(LINK_TOTAL_CODEC, tag, LINK_TOTALS_KEY);
        List<UUID> totalIds = readTaggedList(LINK_TOTAL_ID_CODEC, tag, LINK_TOTAL_IDS_KEY);
        for (int i = 0; i < Math.min(totalIds.size(), totals.size()); i++) {
            this.linkTotals.put(totalIds.get(i), totals.get(i));
        }
        this.poleRuntimes.clear();

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
