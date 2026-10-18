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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p>结算基于「本拍 flush 完、且已叠加转移量之后的账面余量」，结果供下一拍 flush 使用，存在一拍延迟。
 * 这正是真实电力系统的做法，且电网本来就是每 {@code powerGridTick} 拍才算一次，感知不到。</p>
 *
 * <h2>只有一套余量口径（V1.12.7 起）</h2>
 * <p>早期版本刻意区分过两套数：<b>自有</b>余量（不含转移）用于结算分配，<b>入账</b>余量（含转移）用于状态判定。
 * 当时的顾虑是「A 进口后又给 B 送电会自我放大」。那个顾虑是错的：
 * </p>
 * <ul>
 *   <li>结算在<b>一拍之内只跑一趟</b>，用的是一个被逐次扣减的余量数组，不可能超发；</li>
 *   <li>进口量来自别的电网，是确定的数，不会因为转手而变大；</li>
 *   <li>下一拍 flush 会把「本拍实际进出的量」重新记进账本，于是多跳中继自然收敛。</li>
 * </ul>
 * <p>而两套口径的代价是致命的：<b>中继链断在中转站</b>——末地那根跨维度杆自己 `range = 0`、
 * 账面自有余额恒为 0，于是它永远「没有富余可以给出」，从它出发的第二段链路永远不成交。
 * 主世界 → 末地 → 普通杆这种两跳拓扑因此完全不通。</p>
 * <p>现在只剩 {@link #bookedBalance} 一套口径，不存在「漏改某一处」的可能。</p>
 *
 * <h2>M8：亏电断路 / 重连申请 / 开关重启</h2>
 * <p>三者都在本类的每拍结算里顺次执行，顺序是：<b>观察开关跃变 → 更新链路状态 → 结算 →
 * 更新净流出窗口 → 亏电断路 → 重连裁决</b>。</p>
 *
 * <h2>M12：重连规则改版</h2>
 * <p>重连裁决从「冷却够长 + 主端瞬时余额不为负就复活」改为
 * <b>从端按周期申请 → 主端按平均富余批量裁决（先到先得、批出去的部分扣减）</b>，
 * 见 {@link #applyReconnect}。亏电断路与开关重启未变。</p>
 *
 * <h2>M12：进世界补齐每杆的排序设定（V1.12.13）</h2>
 * <p>页面上不再有「覆盖 / 跟随存档默认」开关——每根杆自己持有一份排序设定。尚未配置的杆在
 * {@link #settle()} 的头部被补上一份存档默认快照（{@code WireLinkRegistry.backfillReconnectPolicies}），
 * 已经配置过的杆原样跳过、绝不覆盖。</p>
 */
public final class PowerInterconnectManager {
    /** 本拍算出的、供下一拍 flush 叠加的净进口（正）/ 净出口（负）。 */
    private static final Map<PowerGrid, Integer> DELTAS = new IdentityHashMap<>();

    private PowerInterconnectManager() {
    }

    // ------------------------------------------------------------ 被 Mixin 调用

    /** 该电网本拍应叠加的净进口（正）/ 净出口（负）。 */
    public static int deltaFor(PowerGrid grid) {
        return DELTAS.getOrDefault(grid, 0);
    }

    /** 由 {@code PowerGridManager#tick} 的尾部调用：所有电网都 tick 完之后做一次结算。 */
    public static void settle() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !isSettlementTick(server)) return;

        WireLinkRegistry registry = WireLinkRegistry.get(server);

        // 进世界后的头一次结算就把「还没有自己排序设定的杆」补上一份存档默认并落盘（V1.12.13）。
        //
        // 为什么选这里（而不是 Mixin 本体的世界加载事件）：链路与杆配置的权威副本就是这里的 registry，
        // 「哪些杆参与链路」也只有在结算时才天然可得（registry.linkedPoles()，端点全部来自 LinkDefinition，
        // 不涉及任何杆型判断）。放在整条结算链的最前面，本拍后面每一处读配置的地方
        // （结算优先级、reconnect 裁决里的 policyFor）读到的都是补齐之后的确定值，不必各自再兜一次底。
        //
        // 幂等性从哪来：补齐规则是 ReconnectPolicy.materialize —— 非空原样返回、空才写入存档默认。
        // 第一次调用后那些杆就有了自己的设定（等价于存档默认的一份快照），于是第二次、第三次读到的都是
        // 非空，直接跳过，既不重写也不广播。也就是说「重复执行不会改动已配置的杆」是规则本身保证的，
        // 不是靠调用方去重。
        //
        // 只有真的补了东西才广播：同步包是整体下发 PoleEntry 的，每拍无条件发等于每拍重发整份注册表。
        if (registry.backfillReconnectPolicies()) {
            AddonNetwork.broadcastRegistry(server);
        }

        observeSwitchRestarts(server, registry);

        List<PowerGrid> nodes = new ArrayList<>();
        List<Integer> balances = new ArrayList<>();
        Map<PowerGrid, Integer> nodeIds = new IdentityHashMap<>();
        List<InterconnectSettlement.Link> links = new ArrayList<>();
        List<LinkDefinition> settled = new ArrayList<>();

        for (LinkDefinition definition : registry.links()) {
            PowerGrid masterGrid = findGrid(server, definition.master());
            PowerGrid slaveGrid = findGrid(server, definition.slave());
            boolean bothOnline = masterGrid != null && slaveGrid != null && masterGrid != slaveGrid;
            LinkState state = registry.stateOf(definition.id());

            // 挂起 / 待批准不会被「离线又上线」自动解除：必须走重连裁决或开关重启。
            // 否则一条被亏电断路器掐掉的链路，会在任一端区块重载时悄悄复活。
            if (state == LinkState.SUSPENDED || state == LinkState.PENDING) {
                registry.linkRuntime(definition.id()).push(0, 0);
                continue;
            }

            registry.setState(definition.id(), bothOnline ? LinkState.ACTIVE : LinkState.INACTIVE);
            if (!bothOnline) {
                registry.linkRuntime(definition.id()).push(0, 0);
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
        }

        Map<PowerGrid, Integer> deltas = new IdentityHashMap<>();
        if (!links.isEmpty()) {
            int[] balanceArray = new int[balances.size()];
            for (int i = 0; i < balanceArray.length; i++) balanceArray[i] = balances.get(i);

            // 先全部记 0，再让真正成交的拍覆盖，这样窗口会自动衰减。
            for (LinkDefinition definition : settled) {
                registry.linkRuntime(definition.id()).push(0, 0);
            }

            List<InterconnectSettlement.Transfer> transfers =
                InterconnectSettlement.settle(balanceArray, links);
            double crossDimensionLoss = AnvilCraftPullWires.CONFIG.transmission.crossDimensionalPoleLoss;
            for (InterconnectSettlement.Transfer transfer : transfers) {
                int index = transfer.linkIndex();
                LinkDefinition settledLink = settled.get(index);
                // 主端「送出的量」与从端「收到的量」是两个数：跨维度时损耗让后者更小。
                int sent = transfer.amount();
                // 损耗只作用于「真的跨了维度」的那条链路：两端同维度时不存在传送这回事。
                int received = settledLink.master().dimension().equals(settledLink.slave().dimension())
                    ? sent
                    : InterconnectSettlement.deliveredAmount(sent, crossDimensionLoss);

                // ⑤ 记进二元账：主端这一拍确实多花了 sent，从端确实多得了 received。
                // 注意两边不必相等——差额就是被耗散掉的功率，天经地义地不属于任何一方。
                deltas.merge(nodes.get(transfer.fromNode()), -sent, Integer::sum);
                deltas.merge(nodes.get(transfer.toNode()), received, Integer::sum);

                // ③ 每对主从的监控：窗口按「送出量」衰减，断路排序也用它。
                registry.linkRuntime(settledLink.id()).push(sent, received);
            }
        }

        // 整体替换而不是增量更新，这样已经消失的电网不会残留脏数据。
        DELTAS.clear();
        DELTAS.putAll(deltas);
        publishStats(server, registry, deltas);

        // 顺序含义：先用「这一拍的实际净流出」更新窗口，再据此决定要不要断开。
        // 先记录每根杆所在电网的本拍余额，重连裁决才能取到「最近一段时间的平均」。
        // 放在断路之前：断路本身会改变链路状态，但余额是这一拍客观观测到的值。
        recordPoleBalances(server, registry);
        tickReconnectCooldowns(registry);
        shedDeficitLinks(server, registry);
        applyReconnect(server, registry);
    }

    /** 退出世界时清空，避免跨存档残留。 */
    public static void clear() {
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
     *
     * <p><b>判据是账面余额</b>：转移在 V1.12.3 起记进了 {@code generate}/{@code consume}，
     * 所以 {@link PowerGrid#getRemaining()} 已经把「为了养这些链路要付出多少」算进去了。
     * 自有发电 160、自用 160、外送 40 的主端，账面上是 -40 —— 它在亏电，该断。</p>
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

            int balance = bookedBalance(grid);
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

    // ------------------------------------------------------------ M12：挂起重连状态机

    /**
     * 挂起链路的从端按周期申请、主端按<b>平均富余</b>批量裁决。
     *
     * <h2>本条规则取代了 V1.8.0 的旧重连规则</h2>
     * <p>旧规则是「冷却够长 + 从端电网 {@code isWorking()} 持续 {@code reconnectStableTicks} 拍 +
     * 主端<b>瞬时账面余额</b>不为负就复活」。它的两个毛病：</p>
     * <ul>
     *   <li>主端用<b>瞬时</b>余额判定——抖一下放一条、放完立刻又亏，来回震荡；</li>
     *   <li>多个从端同一拍申请时<b>各批各的</b>——一个只有 100 富余的主端会同时批准两个各要 80 的从端。</li>
     * </ul>
     * <p>新规则的计时起点也不再需要「电网稳定」这个前置条件：申请周期由
     * {@link PoleRuntime#tickRequest} 独自决定（{@code reconnectStableTicks} 随之作废）。
     * 旧规则在 {@code IMPLEMENTATION.md} §11.3 记为历史条目。</p>
     */
    private static void applyReconnect(MinecraftServer server, WireLinkRegistry registry) {
        int step = PowerGrid.gridInterval();
        int interval = Math.max(step, AnvilCraftPullWires.CONFIG.settlement.reconnectRequestInterval);

        // ① 申请计时是「杆」级的（PoleRuntime.requestTicks）：一根从端杆每拍只推进一次。
        //    它同时是多根主端的从端时，挂起的几条链路共用这一个申请周期。
        Set<PoleKey> requesters = new LinkedHashSet<>();
        for (LinkDefinition definition : registry.links()) {
            if (isAwaitingReconnect(registry, definition)) requesters.add(definition.slave());
        }
        Set<PoleKey> firing = new LinkedHashSet<>();
        for (PoleKey slave : requesters) {
            if (registry.runtime(slave).tickRequest(step, interval)) firing.add(slave);
        }
        if (firing.isEmpty()) return;

        // ② 本拍到点的申请按主端分组：同一个主端的申请放在一起裁决，才能先到先得、不超发。
        Map<PoleKey, List<PendingRequest>> byMaster = new LinkedHashMap<>();
        for (LinkDefinition definition : registry.links()) {
            if (!isAwaitingReconnect(registry, definition)) continue;
            if (!firing.contains(definition.slave())) continue;
            // 冷却期内的链路本轮先不申请，等下一个周期再试（只有冷却配置大于申请周期时才会发生）。
            if (registry.linkRuntime(definition.id()).cooldownTicks() > 0) continue;
            // 两端都必须在线。若主端或从端此刻不在电网里，批准出来的链路会在下一拍结算时
            // 被改判成 INACTIVE，之后随区块加载自动复活——那等于绕过了整套裁决。
            if (findGrid(server, definition.master()) == null) continue;
            if (findGrid(server, definition.slave()) == null) continue;

            PoleRuntime slaveRuntime = registry.runtime(definition.slave());
            int demand = slaveRuntime.averageDemand();
            byMaster
                .computeIfAbsent(definition.master(), ignored -> new ArrayList<>())
                .add(new PendingRequest(
                    definition,
                    demand,
                    // 三个排序键全部来自已有数据：优先级取从端杆的配置、申请量取从端的平均缺口、
                    // 时间取链路建立序号。没有任何一份是为此新开的存储。
                    new ReconnectPolicy.Request(
                        registry.config(definition.slave()).priority(),
                        demand,
                        definition.createdAt()
                    )
                ));
        }

        boolean changed = false;
        for (Map.Entry<PoleKey, List<PendingRequest>> entry : byMaster.entrySet()) {
            PoleKey master = entry.getKey();
            List<PendingRequest> requests = entry.getValue();
            ReconnectPolicy policy = policyFor(registry, master);
            requests.sort((a, b) -> policy.compare(a.order(), b.order()));

            PoleRuntime masterRuntime = registry.runtime(master);
            // 窗口还没热时里面是 0：把「还没观测」当成「没有富余」会白白拒掉重启后的第一次申请。
            if (!masterRuntime.balanceWindowWarm()) continue;

            int surplus = masterRuntime.averageSurplus();
            for (PendingRequest request : requests) {
                if (!ReconnectPolicy.approve(surplus, request.demand())) continue;

                LinkDefinition link = request.link();
                registry.setState(link.id(), LinkState.ACTIVE);
                LinkRuntime linkRuntime = registry.linkRuntime(link.id());
                linkRuntime.resetWindow();
                linkRuntime.setCooldownTicks(0);
                PoleRuntime slaveRuntime = registry.runtime(link.slave());
                slaveRuntime.setRequestTicks(0);
                slaveRuntime.setReconnectCooldownTicks(0);
                // 已经批出去的份额要从剩余富余里扣掉，否则同一根主端的每个申请都会各自把
                // 这份富余用一遍——那正是批量裁决要防的超发。
                surplus -= request.demand();
                changed = true;
            }
        }
        if (changed) {
            // 状态是运行时值、不入存档，但客户端要靠它给电力线上色，因此必须主动广播。
            AddonNetwork.broadcastRegistry(server);
        }
    }

    /**
     * 该主端用来排序重连申请的排序策略。
     *
     * <p>取自<b>这根主端杆自己的配置</b>：{@link WireLinkRegistry#policyOf} 先看它有没有自己那一份，
     * 没有才退回存档级默认（那只会发生在补齐之前的短暂窗口里）。排序本身由纯逻辑
     * {@link ReconnectPolicy} 承担（含自检），
     * 这里只是把「哪份策略」取出来——{@link #applyReconnect} 一行都不用改。</p>
     *
     * @param registry 链路注册表
     * @param master   主端杆
     */
    private static ReconnectPolicy policyFor(WireLinkRegistry registry, PoleKey master) {
        return registry.policyOf(master);
    }

    /** 该链路是否在「等重连」：被亏电断路器挂起，或已在等主端批准。 */
    private static boolean isAwaitingReconnect(WireLinkRegistry registry, LinkDefinition definition) {
        LinkState state = registry.stateOf(definition.id());
        return state == LinkState.SUSPENDED || state == LinkState.PENDING;
    }

    /**
     * 本拍到点的一次申请：排序键是 {@link ReconnectPolicy.Request}，另外记住它属于哪条链路。
     *
     * <p>只是这一拍的临时收集（方法返回即丢），<b>不构成第二套状态</b>。</p>
     */
    private record PendingRequest(LinkDefinition link, int demand, ReconnectPolicy.Request order) {
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
                // 同一个数不能两边都用：主端读「送出」（它失去的），从端读「收到」（它得到的）。
                if (asMaster) {
                    sendNow += runtime.lastSent();
                } else {
                    recvNow += runtime.lastReceived();
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

        // 每对主从的本拍转移量也一起发下去：常驻连接列表要按条显示，
        // 而按杆汇总的 Entry 无法回答「这条链路本拍走了多少」。
        List<SyncLinkStatsPacket.StateEntry> states = new ArrayList<>();
        for (LinkDefinition definition : registry.links()) {
            LinkRuntime runtime = registry.linkRuntime(definition.id());
            states.add(new SyncLinkStatsPacket.StateEntry(
                definition.id(), registry.stateOf(definition.id()),
                runtime.lastSent(), runtime.lastReceived()
            ));
        }
        AddonNetwork.broadcastStats(server, stats, states);
    }

    // ------------------------------------------------------------ 内部

    /**
     * 该电网当前的<b>账面余额</b>：自有余额加上本拍已经记进来的转移量，即 {@link PowerGrid#getRemaining()}。
     *
     * <p>全模组<b>只有这一个</b>余量口径——结算分配、亏电断路、重连裁决、电网统计全部用它，
     * 因此不存在「某个地方读了另一套数」的可能。</p>
     *
     * <p>为什么结算分配也能安全地用它：结算在一拍之内只跑一趟，用一个被逐次扣减的余量数组分配，
     * 不可能超发；进口量来自别的电网，是确定的数，不会因为转手而变大；
     * 下一拍 flush 会把本拍实际进出的量重新记进账本。中继链正是靠这一点才能导通。</p>
     */
    private static int bookedBalance(PowerGrid grid) {
        return grid.getRemaining();
    }

    /**
     * 该电网的<b>自有余额</b>：本网元件之和，<b>不含</b>我们注入的转移量。
     *
     * <p><b>它是从账面值直接推出来的，不是第二份状态</b>：账本 = 自有 + 本拍已注入的增量，
     * 而那个增量正是 {@link #deltaFor}（结算结束前它一直保持上一轮的值）。
     * 所以这个「双口径」永远不可能出现某一处没跟着改的情况——它压根没有存储。</p>
     *
     * <p><b>用途只有一个：判断从端缺多少。</b> 需求侧必须用它，否则会出现
     * <b>隔一拍闪一次</b>的自抵消：第 N 拍按缺口 20 送电、记下 +20；第 N+1 拍从端账面
     * 已经变成 0，结算便判定「它不缺」而跳过；第 N+2 拍又缺 20……如此往复。
     * 上一拍给出的电把这一拍的需求抵消掉，正是这个振荡的成因。</p>
     */
    private static int ownBalance(PowerGrid grid) {
        return bookedBalance(grid) - deltaFor(grid);
    }

    /**
     * 把每根参与链路的杆所在电网的<b>本拍账面余额</b>记进它的余额窗口。
     *
     * <p>一个窗口供应两个角色：主端用它算平均富余，从端用它算平均缺口。
     * 记录时机在亏电断路与重连裁决<b>之前</b>——余额是这一拍客观观测到的值，
     * 不该被同一拍里我们自己的裁决动作污染。</p>
     */
    private static void recordPoleBalances(MinecraftServer server, WireLinkRegistry registry) {
        for (PoleKey key : registry.linkedPoles()) {
            PowerGrid grid = findGrid(server, key);
            registry.runtime(key).recordBalance(grid == null ? 0 : bookedBalance(grid));
        }
    }
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
        // 数组里放的是「自有余额」：结算的取量公式 `-remaining[slave]` 读的就是它，
        // 于是需求每拍都从同一个起点重算，不会被上一拍的给予抵消。
        balances.add(ownBalance(grid));
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
