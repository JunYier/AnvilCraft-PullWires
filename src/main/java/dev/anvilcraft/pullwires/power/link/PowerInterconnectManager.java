package dev.anvilcraft.pullwires.power.link;

import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 链路互联的运行期调度：把 {@link InterconnectSettlement} 的结果接回本体的电网。
 *
 * <h2>为什么要靠「flush 增量」而不是往电网里塞虚拟元件</h2>
 * <p>本体能往电网里注入功率的现成手段只有两种，都不合用：</p>
 * <ul>
 *   <li>{@code DynamicPowerComponent} 必须绑定一个 {@code Entity}，而输电杆是方块；</li>
 *   <li>自建 {@code IPowerStorage} 端点要靠 {@code PowerGrid.add()} 入网，会被算进
 *       {@code connections} 邻接图、同步给客户端、并参与电网外框绘制，污染面太大。</li>
 * </ul>
 * <p>改为：电网每次 {@code flush()} 时会从零重算 {@code generate}/{@code consume}，
 * 我们在这次重算<b>之后</b>叠加一个「本电网净进口（正）/ 净出口（负）」增量。
 * 增量由本管理器保存，因此每拍都会被重新应用，不需要改动本体的任何元件集合。</p>
 *
 * <h2>控制环与一拍延迟</h2>
 * <p>结算基于「本拍 flush 出来的自有余量」，结果供下一拍 flush 使用，存在一拍延迟。
 * 这正是真实电力系统的做法，且电网本来就是每 {@code powerGridTick} 拍才算一次，感知不到。</p>
 */
public final class PowerInterconnectManager {
    /** 本拍各电网 flush 出来的<b>自有</b>余量（不含我们注入的增量）。 */
    private static final Map<PowerGrid, Integer> RAW_BALANCES = new IdentityHashMap<>();

    /** 本拍算出的、供下一拍 flush 叠加的净进口（正）/ 净出口（负）。 */
    private static final Map<PowerGrid, Integer> DELTAS = new IdentityHashMap<>();

    private PowerInterconnectManager() {
    }

    // ------------------------------------------------------------ 被 Mixin 调用

    /** 由 {@code PowerGridManager#tick} 的头部调用：开始收集本拍数据。 */
    public static void beginTick() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !isSettlementTick(server)) return;
        RAW_BALANCES.clear();
    }

    /** 由 {@code PowerGrid#flush} 的尾部调用：记录自有余量，并叠加上一拍算出的增量。 */
    public static void recordRawBalance(PowerGrid grid, int rawBalance) {
        RAW_BALANCES.put(grid, rawBalance);
    }

    /** 该电网本拍应叠加的净进口（正）/ 净出口（负）。 */
    public static int deltaFor(PowerGrid grid) {
        return DELTAS.getOrDefault(grid, 0);
    }

    /** 由 {@code PowerGridManager#tick} 的尾部调用：所有电网都 tick 完之后做一次结算。 */
    public static void settle() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !isSettlementTick(server)) return;

        WireLinkRegistry registry = WireLinkRegistry.get(server);
        List<PowerGrid> nodes = new ArrayList<>();
        List<Integer> balances = new ArrayList<>();
        Map<PowerGrid, Integer> nodeIds = new IdentityHashMap<>();
        List<InterconnectSettlement.Link> links = new ArrayList<>();
        List<LinkDefinition> settled = new ArrayList<>();

        for (LinkDefinition definition : registry.links()) {
            PowerGrid masterGrid = findGrid(server, definition.master());
            PowerGrid slaveGrid = findGrid(server, definition.slave());
            if (masterGrid == null || slaveGrid == null || masterGrid == slaveGrid) {
                // 任一端未入网（杆被关掉、区块卸载、或两端本来就在同一座电网里）。
                registry.setState(definition.id(), LinkState.INACTIVE);
                continue;
            }
            registry.setState(definition.id(), LinkState.ACTIVE);
            links.add(new InterconnectSettlement.Link(
                links.size(),
                nodeId(masterGrid, nodeIds, nodes, balances),
                nodeId(slaveGrid, nodeIds, nodes, balances),
                registry.config(definition.master()).priority()
            ));
            settled.add(definition);
        }

        if (links.isEmpty()) {
            DELTAS.clear();
            return;
        }

        int[] balanceArray = new int[balances.size()];
        for (int i = 0; i < balanceArray.length; i++) balanceArray[i] = balances.get(i);

        Map<PowerGrid, Integer> deltas = new IdentityHashMap<>();
        for (InterconnectSettlement.Transfer transfer : InterconnectSettlement.settle(balanceArray, links)) {
            deltas.merge(nodes.get(transfer.fromNode()), -transfer.amount(), Integer::sum);
            deltas.merge(nodes.get(transfer.toNode()), transfer.amount(), Integer::sum);
        }

        // 整体替换而不是增量更新，这样已经消失的电网不会残留脏数据。
        DELTAS.clear();
        DELTAS.putAll(deltas);
    }

    /** 退出世界时清空，避免跨存档残留。 */
    public static void clear() {
        RAW_BALANCES.clear();
        DELTAS.clear();
    }

    // ------------------------------------------------------------ 内部

    private static boolean isSettlementTick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld != null && overworld.getGameTime() % PowerGrid.gridInterval() == 0;
    }

    private static int nodeId(
        PowerGrid grid, Map<PowerGrid, Integer> nodeIds, List<PowerGrid> nodes, List<Integer> balances
    ) {
        Integer existing = nodeIds.get(grid);
        if (existing != null) return existing;
        int id = nodes.size();
        nodeIds.put(grid, id);
        nodes.add(grid);
        balances.add(RAW_BALANCES.getOrDefault(grid, grid.getRemaining()));
        return id;
    }

    /**
     * 按坐标找到该杆所在电网。
     *
     * <p>不能用 {@code PowerGrid.findPowerGridContains}：它只判断坐标是否落在某座电网的包围盒里，
     * 区块卸载后杆虽然已退出电网，坐标仍可能落在邻近电网的范围内，会产生假阳性。
     * 这里改为检查电网的元件集合里是否真的有位于该坐标的元件。
     * 注意输电杆以<b>顶段</b>为元件，因此 {@link PoleKey} 必须存顶段坐标。</p>
     */
    private static @Nullable PowerGrid findGrid(MinecraftServer server, PoleKey key) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null) return null;
        for (PowerGrid grid : PowerGrid.MANAGER.getGridSet(level)) {
            for (IPowerComponent component : grid.getComponents()) {
                if (component.getPos().equals(key.pos())) return grid;
            }
        }
        return null;
    }
}
