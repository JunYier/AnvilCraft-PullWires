package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.network.SyncLinkStatsPacket;
import dev.anvilcraft.pullwires.network.SyncRegistryPacket;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.anvilcraft.pullwires.power.link.PoleConfig;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PolePriority;
import dev.anvilcraft.pullwires.power.link.WireLinkRegistry;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端侧的注册表镜像。
 *
 * <p>服务端权威：待连接主端由 {@link dev.anvilcraft.pullwires.network.SyncPendingLinkPacket} 下发，
 * 链路定义与杆配置由 {@link SyncRegistryPacket} 下发。客户端只读它，
 * 用来画粒子束、画手动连接的电力线，以及显示瞄准中那根杆的优先级。</p>
 */
public final class ManualLinkClientState {
    private static List<LinkDefinition> links = List.of();
    private static Map<PoleKey, PoleConfig> poleConfigs = Map.of();
    private static Map<PoleKey, SyncLinkStatsPacket.Entry> stats = Map.of();
    private static Map<UUID, LinkState> states = Map.of();

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
    }

    /** 未同步到的杆返回默认配置（优先级 7、未锁定）。 */
    public static PoleConfig configOf(PoleKey key) {
        return poleConfigs.getOrDefault(key, new PoleConfig(PolePriority.DEFAULT, false));
    }

    /** 接收每拍的转移量统计。整体替换，避免残留上一拍的数值。 */
    public static void applyStats(SyncLinkStatsPacket packet) {
        Map<PoleKey, SyncLinkStatsPacket.Entry> map = new HashMap<>();
        for (SyncLinkStatsPacket.Entry entry : packet.entries()) {
            map.put(entry.pole(), entry);
        }
        stats = Map.copyOf(map);

        Map<UUID, LinkState> stateMap = new HashMap<>();
        for (SyncLinkStatsPacket.StateEntry entry : packet.states()) {
            stateMap.put(entry.id(), entry.state());
        }
        states = Map.copyOf(stateMap);
    }

    /** 链路状态；尚未同步到时按 ACTIVE 处理（与注册表的默认值一致）。 */
    public static LinkState stateOf(UUID id) {
        return states.getOrDefault(id, LinkState.ACTIVE);
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
    }
}
