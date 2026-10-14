package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.network.SyncLinkStatsPacket;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
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
 *
 * <h2>M8：亏电断路 / 重连申请 / 开关重启</h2>
 * <p>三者都在本类的每拍结算里顺次执行，顺序是：<b>观察开关跃变 → 更新链路状态 → 结算 →
 * 更新净流出窗口 → 亏电断路 → 重连裁决</b>。</p>
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
        observeSwitchRestarts(server, registry);

        List<PowerGrid> nodes = new ArrayList<>();
        List<Integer> balances = new ArrayList<>();
        Map<PowerGrid, Integer> nodeIds = new IdentityHashMap<>();
        List<InterconnectSettlement.Link> links = new ArrayList<>();
        List<LinkDefinition> settled = new ArrayList<>();
        List<PowerGrid> settledMasters = new ArrayList<>();
        List<PowerGrid> settledSlaves = new ArrayList<>();

        for (LinkDefinition definition : registry.links()) {
            PowerGrid masterGrid = findGrid(server, definition.master());
            PowerGrid slaveGrid = findGrid(server, definition.slave());
            boolean bothOnline = masterGrid != null && slaveGrid != null && masterGrid != slaveGrid;
            LinkState state = registry.stateOf(definition.id());

            // 挂起 / 待批准不会被「离线又上线」自动解除：必须走重连裁决或开关重启。
            // 否则一条被亏电断路器掐掉的链路，会在任一端区块重载时悄悄复活。
            if (state == LinkState.SUSPENDED || state == LinkState.PENDING) {
                registry.linkRuntime(definition.id()).push(0);
                continue;
            }

            registry.setState(definition.id(), bothOnline ? LinkState.ACTIVE : LinkState.INACTIVE);
            if (!bothOnline) {
                registry.linkRuntime(definition.id()).push(0);
                continue;
            }

            links.add(new InterconnectSettlement.Link(
                links.size(),
                nodeId(masterGrid, nodeIds, nodes, balances),
                nodeId(slaveGrid, nodeIds, nodes, balances),
                registry.config(definition.master()).priority(),
                definition.createdAt()
            ));
            settled.add(definition);
            settledMasters.add(masterGrid);
            settledSlaves.add(slaveGrid);
        }

        Map<PowerGrid, Integer> deltas = new IdentityHashMap<>();
        if (!links.isEmpty()) {
            int[] balanceArray = new int[balances.size()];
            for (int i = 0; i < balanceArray.length; i++) balanceArray[i] = balances.get(i);

            // 先全部记 0，再让真正成交的拍覆盖，这样窗口会自动衰减。
            for (LinkDefinition definition : settled) {
                registry.linkRuntime(definition.id()).push(0);
            }

            List<InterconnectSettlement.Transfer> transfers =
                InterconnectSettlement.settle(balanceArray, links);
            double crossDimensionLoss = AnvilCraftPullWires.CONFIG.transmission.crossDimensionalPoleLoss;
            for (InterconnectSettlement.Transfer transfer : transfers) {
                int index = transfer.linkIndex();
                LinkDefinition settledLink = settled.get(index);
                // 损耗只作用于「真的跨了维度」的那条链路：两端同维度时不存在传送这回事。
                int delivered = settledLink.master().dimension().equals(settledLink.slave().dimension())
                    ? transfer.amount()
                    : InterconnectSettlement.deliveredAmount(transfer.amount(), crossDimensionLoss);

                deltas.merge(nodes.get(transfer.fromNode()), -transfer.amount(), Integer::sum);
                deltas.merge(nodes.get(transfer.toNode()), delivered, Integer::sum);

                // 净流出以「主端给出去」为正；主端若是接收方，这条链路记负数。
                // 注意这里记的是**送出量**而不是送达量：损耗从系统里消失，不该记成主端少给了。
                int signed = nodes.get(transfer.fromNode()) == settledMasters.get(index)
                    ? transfer.amount()
                    : -transfer.amount();
                registry.linkRuntime(settledLink.id()).push(signed);
            }
        }

        // 整体替换而不是增量更新，这样已经消失的电网不会残留脏数据。
        DELTAS.clear();
        DELTAS.putAll(deltas);
        publishStats(server, registry, deltas);

        // 顺序含义：先用「这一拍的实际净流出」更新窗口，再据此决定要不要断开。
        tickReconnectCooldowns(registry);
        shedDeficitLinks(server, registry);
        runReconnect(server, registry);
    }

    /** 退出世界时清空，避免跨存档残留。 */
    public static void clear() {
        RAW_BALANCES.clear();
        DELTAS.clear();
    }

    // ------------------------------------------------------------ M8：开关重启

    /**
     * 观察所有被链路引用的杆的 {@code SWITCH}，处理 OFF → ON 跃变。
     *
     * <p><b>首次观察不算跃变</b>（由 {@link PoleRuntime#observeSwitch} 保证），
     * 否则每次进游戏都会把所有链路无条件重启，亏电计时永远清零。</p>
     *
     * <p>区块未加载时跳过：读未加载区块会强制加载，代价远大于收益。
     * 这种情况下的状态变化由「电网是否存在」自然反映，只是拿不到「开关重启」这个事件。</p>
     */
    private static void observeSwitchRestarts(MinecraftServer server, WireLinkRegistry registry) {
        if (!AnvilCraftPullWires.CONFIG.transmission.switchRestartResetsRuntime) return;

        for (PoleKey key : registry.linkedPoles()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !level.isLoaded(key.pos())) continue;
            BlockState state = level.getBlockState(key.pos());
            if (!state.hasProperty(IPowerComponent.SWITCH)) continue;
            boolean on = state.getValue(IPowerComponent.SWITCH) == IPowerComponent.Switch.ON;
            if (!registry.observeSwitch(key, on)) continue;

            registry.resetRuntime(key);
            for (LinkDefinition definition : registry.linksOf(key)) {
                LinkState linkState = registry.stateOf(definition.id());
                if (linkState != LinkState.SUSPENDED && linkState != LinkState.PENDING) continue;
                registry.setState(definition.id(), LinkState.ACTIVE);
                LinkRuntime runtime = registry.linkRuntime(definition.id());
                runtime.resetWindow();
                runtime.setCooldownTicks(0);
            }
        }
    }

    // ------------------------------------------------------------ M8：亏电断路

    /**
     * 主端连续亏电超过阈值时，按平均净流出降序挂起链路。
     *
     * <p><b>为什么用「预测」而不是「重测」</b>：挂起产生的效果要等下一拍 flush 才反映到电网余量上，
     * 在同一拍里反复读余量永远读到同一个值。所以这里维护一个「剩余缺口」，
     * 每挂起一条就按它的平均净流出扣减，扣到不为正即停——等价于「每断一条重新评估」。</p>
     */
    private static void shedDeficitLinks(MinecraftServer server, WireLinkRegistry registry) {
        int threshold = AnvilCraftPullWires.CONFIG.settlement.manualLinkDeficitTicks;
        if (threshold <= 0) return;

        for (PoleKey master : registry.linkedPoles()) {
            List<LinkDefinition> mastered = registry.linksMasteredBy(master);
            if (mastered.isEmpty()) continue;

            PowerGrid grid = findGrid(server, master);
            PoleRuntime runtime = registry.runtime(master);
            if (grid == null) {
                runtime.setDeficitTicks(0);
                continue;
            }

            int balance = RAW_BALANCES.getOrDefault(grid, grid.getRemaining());
            if (balance >= 0) {
                runtime.setDeficitTicks(0);
                continue;
            }

            runtime.setDeficitTicks(runtime.deficitTicks() + PowerGrid.gridInterval());
            if (runtime.deficitTicks() < threshold) continue;

            List<LinkDefinition> candidates = new ArrayList<>();
            for (LinkDefinition definition : mastered) {
                if (registry.stateOf(definition.id()) != LinkState.ACTIVE) continue;
                if (registry.linkRuntime(definition.id()).averageNetOut() <= 0) continue;
                candidates.add(definition);
            }
            candidates.sort(Comparator
                .comparingInt((LinkDefinition d) -> registry.linkRuntime(d.id()).averageNetOut())
                .reversed());

            int remaining = -balance;
            for (LinkDefinition definition : candidates) {
                LinkRuntime runtimeLink = registry.linkRuntime(definition.id());
                int netOut = runtimeLink.averageNetOut();
                registry.setState(definition.id(), LinkState.SUSPENDED);
                runtimeLink.setCooldownTicks(AnvilCraftPullWires.CONFIG.settlement.manualLinkReconnectCooldown);
                runtimeLink.resetWindow();
                remaining -= netOut;
                if (remaining <= 0) break;
            }
            // 处理完一轮重新计时，避免下一拍立刻又断一条。
            runtime.setDeficitTicks(0);
        }
    }

    // ------------------------------------------------------------ M8：重连申请

    /**
     * 从端电网稳定后按周期申请重连，主端不亏电则同意。
     *
     * <p>「电网稳定」实现为「从端所在电网连续 {@code reconnectStableTicks} 拍 {@code isWorking()}」，
     * 期间一旦中断就重新计时。</p>
     */
    private static void runReconnect(MinecraftServer server, WireLinkRegistry registry) {
        int step = PowerGrid.gridInterval();
        int stableTicks = AnvilCraftPullWires.CONFIG.settlement.reconnectStableTicks;
        int requestInterval = Math.max(step, AnvilCraftPullWires.CONFIG.settlement.reconnectRequestInterval);

        for (LinkDefinition definition : registry.links()) {
            LinkState state = registry.stateOf(definition.id());
            if (state != LinkState.SUSPENDED && state != LinkState.PENDING) continue;

            LinkRuntime runtime = registry.linkRuntime(definition.id());
            if (runtime.cooldownTicks() > 0) continue;

            PowerGrid slaveGrid = findGrid(server, definition.slave());
            PoleRuntime slaveRuntime = registry.runtime(definition.slave());
            if (slaveGrid == null || !slaveGrid.isWorking()) {
                slaveRuntime.setRequestTicks(0);
                continue;
            }

            slaveRuntime.setRequestTicks(slaveRuntime.requestTicks() + step);
            if (slaveRuntime.requestTicks() < stableTicks) continue;
            if (slaveRuntime.requestTicks() % requestInterval != 0) continue;

            PowerGrid masterGrid = findGrid(server, definition.master());
            if (masterGrid == null) continue;
            if (RAW_BALANCES.getOrDefault(masterGrid, masterGrid.getRemaining()) < 0) continue;

            registry.setState(definition.id(), LinkState.ACTIVE);
            runtime.setCooldownTicks(0);
            runtime.resetWindow();
            slaveRuntime.setRequestTicks(0);
        }
    }

    private static void tickReconnectCooldowns(WireLinkRegistry registry) {
        int step = PowerGrid.gridInterval();
        for (LinkDefinition definition : registry.links()) {
            registry.linkRuntime(definition.id()).tickCooldown(step);
        }
    }

    /**
     * 汇总每根杆的转移量统计并推给客户端。
     *
     * <p>放在结算的最后一步，是因为它需要读取<b>本拍刚写入</b>的净流量（{@code deltas}）与累计量。
     * 「总转移电量」取持久化的累计值，因此跨会话保留；「发送 / 接收电量」是本拍瞬时值。</p>
     *
     * <p>电网的净流出 / 净流入直接取自 {@code deltas}——它本身就是「该电网本拍的净进口（正）/ 净出口（负）」，
     * 不需要再单独统计一遍。</p>
     */
    private static void publishStats(
        MinecraftServer server, WireLinkRegistry registry, Map<PowerGrid, Integer> deltas
    ) {
        List<SyncLinkStatsPacket.Entry> stats = new ArrayList<>();
        for (PoleKey pole : registry.linkedPoles()) {
            int sendNow = 0;
            int recvNow = 0;
            for (LinkDefinition definition : registry.links()) {
                boolean asMaster = definition.master().equals(pole);
                boolean asSlave = definition.slave().equals(pole);
                if (!asMaster && !asSlave) continue;
                LinkRuntime runtime = registry.linkRuntime(definition.id());
                if (asMaster) {
                    sendNow += runtime.lastAmount();
                } else {
                    recvNow += runtime.lastAmount();
                }
            }
            PowerGrid grid = findGrid(server, pole);
            int delta = grid == null ? 0 : deltas.getOrDefault(grid, 0);
            stats.add(new SyncLinkStatsPacket.Entry(
                pole, sendNow, recvNow,
                delta < 0 ? -delta : 0, delta > 0 ? delta : 0
            ));
        }
        registry.setPoleStats(stats);

        List<SyncLinkStatsPacket.StateEntry> states = new ArrayList<>();
        for (LinkDefinition definition : registry.links()) {
            states.add(new SyncLinkStatsPacket.StateEntry(definition.id(), registry.stateOf(definition.id())));
        }
        AddonNetwork.broadcastStats(server, stats, states);
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
        BlockPos pos = key.pos();
        for (PowerGrid grid : PowerGrid.MANAGER.getGridSet(level)) {
            for (IPowerComponent component : grid.getComponents()) {
                if (component.getPos().equals(pos)) return grid;
            }
        }
        return null;
    }
}
