package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.network.SyncLinkStatsPacket;
import dev.anvilcraft.pullwires.network.SyncRegistryPacket;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.anvilcraft.pullwires.power.link.PoleConfig;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PolePriority;
import dev.anvilcraft.pullwires.power.link.ReconnectPolicy;
import dev.anvilcraft.pullwires.power.link.WireLinkRegistry;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 客户端侧的注册表镜像。
 *
 * <p>服务端权威：链路定义与杆配置由 {@link SyncRegistryPacket} 下发，每拍的转移量与链路状态由
 * {@link SyncLinkStatsPacket} 下发。客户端只读它，用来画粒子束、画手动连接的电力线、
 * 判断跨维度杆的连接态，以及显示瞄准中那根杆的优先级。</p>
 *
 * <p>「待连接的主端」<b>不在这里</b>——它写在手持物的绑定数据上（{@code LinkBinding}），
 * 客户端与服务端读到的是同一份。</p>
 */
public final class ManualLinkClientState {
    /**
     * 尚未收到同步包时的内置缺省值。
     *
     * <p>与 {@code WireLinkRegistry.defaultConfig()} 同形（默认优先级、未锁定、还没有自己的排序设定），
     * 只在「进服务器后的第一份 SyncRegistryPacket 到达之前」用得上。</p>
     */
    private static final PoleConfig BUILTIN_DEFAULTS =
        new PoleConfig(PolePriority.DEFAULT, false, Optional.empty());

    private static List<LinkDefinition> links = List.of();
    private static Map<PoleKey, PoleConfig> poleConfigs = Map.of();
    private static Map<PoleKey, SyncLinkStatsPacket.Entry> stats = Map.of();
    private static Map<UUID, SyncLinkStatsPacket.StateEntry> states = Map.of();

    /** 服务端下发的缺省值：没有存档条目的杆该显示什么。 */
    private static WireLinkRegistry.Defaults defaults =
        new WireLinkRegistry.Defaults(BUILTIN_DEFAULTS, ReconnectPolicy.DEFAULT);

    private ManualLinkClientState() {
    }


    public static List<LinkDefinition> links() {
        return links;
    }

    /** 覆盖式应用整份镜像，避免出现「链路是新的、配置还是旧的」这种半更新状态。 */
    public static void apply(SyncRegistryPacket packet) {
        links = List.copyOf(packet.links());
        Map<PoleKey, PoleConfig> configs = new HashMap<>();
        for (WireLinkRegistry.PoleEntry entry : packet.poles()) {
            configs.put(entry.key(), entry.config());
        }
        poleConfigs = Map.copyOf(configs);
        // 缺省值缺失（老服务端发的包）时保留上一份，不把默认优先级与默认排序打回内置值。
        WireLinkRegistry.Defaults received = packet.defaults();
        if (received != null) defaults = received;
    }

    /** 未同步到的杆返回服务端下发的默认配置（优先级、锁定取存档级默认，排序设定则尚未配置）。 */
    public static PoleConfig configOf(PoleKey key) {
        return poleConfigs.getOrDefault(key, defaults.poleConfig());
    }

    /** 存档级默认的重连排序（服务端下发；未同步到时是 {@link ReconnectPolicy#DEFAULT}）。 */
    public static ReconnectPolicy defaultPolicy() {
        return defaults.reconnectPolicy();
    }

    /**
     * 该杆<b>实际生效</b>的重连排序：它自己有一份就用它自己的，还没有就用存档默认。
     *
     * <p>与服务端 {@code WireLinkRegistry.policyOf} 共用同一条解析规则
     * （{@link ReconnectPolicy#resolve}），因此配置页上写着的顺序就是主端裁决时用的顺序。
     * 「还没有」只存在于补齐之前：服务端每拍结算会给未配置的杆补上一份存档默认快照并广播下来。</p>
     */
    public static ReconnectPolicy policyOf(PoleKey key) {
        return ReconnectPolicy.resolve(configOf(key).reconnectPolicy(), defaults.reconnectPolicy());
    }

    /** 接收每拍的转移量统计。整体替换，避免残留上一拍的数值。 */
    public static void applyStats(SyncLinkStatsPacket packet) {
        Map<PoleKey, SyncLinkStatsPacket.Entry> map = new HashMap<>();
        for (SyncLinkStatsPacket.Entry entry : packet.entries()) {
            map.put(entry.pole(), entry);
        }
        stats = Map.copyOf(map);

        Map<UUID, SyncLinkStatsPacket.StateEntry> stateMap = new HashMap<>();
        for (SyncLinkStatsPacket.StateEntry entry : packet.states()) {
            stateMap.put(entry.id(), entry);
        }
        // 空表不覆盖：服务端只要有链路就一定会带上状态，所以「收到空表」几乎总是解码出了问题。
        // 以前无条件替换，结果是状态表被清空 → stateOf 全部回退默认 ACTIVE → 线色再也不变。
        // 保留上一次的状态虽然可能是旧的，但至少不会把一切都伪装成「正常通电」。
        if (stateMap.isEmpty() && !links.isEmpty()) return;
        states = Map.copyOf(stateMap);
    }

    /**
     * 该链路本拍的转移量；尚未同步到时返回 {@code null}。
     *
     * <p>常驻连接列表按条显示用。返回整条 {@code StateEntry} 而不是两个 int，
     * 是为了让调用方能判断「有没有数据」与「数据是不是 0」的区别。</p>
     */
    public static @Nullable SyncLinkStatsPacket.StateEntry amountOf(UUID id) {
        return states.get(id);
    }

    /** 链路状态；尚未同步到时按 ACTIVE 处理（与注册表的默认值一致）。 */
    public static LinkState stateOf(UUID id) {
        SyncLinkStatsPacket.StateEntry entry = states.get(id);
        return entry == null ? LinkState.ACTIVE : entry.state();
    }

    /** 该杆的转移量统计；未同步到（即它不参与任何链路）时为 null。 */
    public static @Nullable SyncLinkStatsPacket.Entry statsOf(PoleKey key) {
        return stats.get(key);
    }

    /** 断开连接时清空，避免把上一条线的信息带到下一个存档。 */
    public static void clear() {
        links = List.of();
        poleConfigs = Map.of();
        stats = Map.of();
        states = Map.of();
        defaults = new WireLinkRegistry.Defaults(BUILTIN_DEFAULTS, ReconnectPolicy.DEFAULT);
    }
}
