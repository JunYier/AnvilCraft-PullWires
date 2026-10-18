package dev.anvilcraft.pullwires.power.link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 链路互联结算：把主端电网的富余按链路<b>单向</b>送给从端电网。
 *
 * <p>纯整数运算，不依赖任何游戏类型，可脱离游戏单独验证。</p>
 *
 * <h2>单向模型（V1.4.1 起）</h2>
 * <p>每条链路是有向边 {@code master → slave}：<b>只有主端能送电给从端</b>，从端的富余不会回流。
 * 因此每条链路只有一个决策者——主端；而 {@code A → B} 与 {@code B → A} 是两条独立的边，互不干扰。</p>
 *
 * <h2>两阶段结算（V1.12.9 起）</h2>
 * <p>早期版本是「一趟遍历、按缺口取量」，这在原理上<b>做不到多跳</b>：
 * 中转站只要自己没有用电器（自有余额为 0），就没有缺口，于是上游链路被
 * {@code remaining[slave] >= 0} 直接跳过，中转站永远拿不到可以转手的电。</p>
 * <p>现在分两阶段：</p>
 * <ol>
 *   <li><b>需求透传</b>：沿链路图把下游的缺口逐级汇总上来，
 *       {@code need[i] = max(0, -own[i]) + Σ(上游要转给下游的量)}。
 *       迭代到不动点（链路成环时限次兜底），于是「C 缺 50」这件事能被最上游的 A 看见。</li>
 *   <li><b>分配</b>：按额度给每条链路定额，{@code amount = min(主端当前可支配, 从端还差多少)}。
 *       同样迭代到不动点，因此「先给中转站、中转站再往下给」这种顺序问题会自行收敛，
 *       不依赖链路的处理顺序。</li>
 * </ol>
 * <p><b>需求侧永远只读 {@code own}（自有余额，不含我们注入的转移量）</b>，
 * 这是防「隔一拍闪一次」的关键：若把上一拍注入的增量也算进需求，
 * 上一拍给出的电就会把这一拍的需求抵消掉，成交退化成隔一拍一次。
 * 见 {@code InterconnectSettlementSelfCheck} 里的振荡回归。</p>
 *
 * <h2>从端选主端的顺序（V1.12.9）</h2>
 * <p>一根杆可以同时是多个主端的从端。此时它按
 * <b>(主端优先级数值升序, 建链时间升序)</b> 逐位申请：先找最慷慨（优先级数值最小）的那位，
 * 它给不够再找次位。这正是「优先级对从端选主端也有作用」的落点——
 * 早期版本只让主端按优先级争夺额度，从端这边完全没有选择权。</p>
 */
public final class InterconnectSettlement {
    private InterconnectSettlement() {
    }

    /**
     * 一条参与结算的有向链路。
     *
     * @param index      调用方给出的稳定下标，用于把结果映射回具体链路
     * @param nodeMaster 主端所在网络的编号（对应 {@code balances} 的下标）
     * @param nodeSlave  从端所在网络的编号
     * @param priority   主端杆的结算优先级，<b>数值越小越慷慨</b>，从端会先向它申请
     * @param createdAt  链路建立序号，越小越早
     */
    public record Link(int index, int nodeMaster, int nodeSlave, int priority, long createdAt) {
    }

    /**
     * 一条成交的传输。
     *
     * @param amount 恒为正，方向恒为 {@code fromNode（主端）→ toNode（从端）}
     */
    public record Transfer(int linkIndex, int fromNode, int toNode, int amount) {
    }

    /** 需求透传的迭代上限：链路数 + 1 已经足够覆盖任何 DAG；成环时靠它兜底，不会死循环。 */
    private static final int MAX_PASSES_SLACK = 1;

    /** 需求值的上限，防止成环时数值无界增长。 */
    private static final long NEED_CAP = Integer.MAX_VALUE / 4L;

    /**
     * 执行一次结算。
     *
     * @param balances 各网络的<b>自有</b>余量，正数表示富余、负数表示缺口；<b>不会被修改</b>
     * @param links    参与结算的有向链路
     * @return 本拍成交的传输，未被满足的缺口不会出现在结果里
     */
    public static List<Transfer> settle(int[] balances, List<Link> links) {
        int[] own = balances.clone();
        int[] need = propagateDemand(own, links);
        return allocate(own, need, links);
    }

    /**
     * 阶段一：把下游的缺口逐级汇总上来。
     *
     * <p>{@code need[i]} = 「本网络这一拍总共需要收到多少」= 自己的缺口 + 要替下游转发的量。
     * 用单调增长的不动点迭代，链路数 + 1 趟足够收敛；成环时限次退出并夹住上限。</p>
     */
    private static int[] propagateDemand(int[] own, List<Link> links) {
        int nodeCount = own.length;
        int[] need = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            need[i] = (int) Math.min(NEED_CAP, Math.max(0, -(long) own[i]));
        }
        if (links.isEmpty()) return need;

        int maxPasses = links.size() + MAX_PASSES_SLACK;
        for (int pass = 0; pass < maxPasses; pass++) {
            // 汇总「每个节点要替下游转发的量」：它的每条出边下游各需要多少。
            long[] downstream = new long[nodeCount];
            for (Link link : links) {
                int master = link.nodeMaster();
                int slave = link.nodeSlave();
                if (master == slave) continue;
                downstream[master] += need[slave];
            }
            boolean changed = false;
            for (int i = 0; i < nodeCount; i++) {
                long candidate = Math.max(0, -(long) own[i]) + downstream[i];
                candidate = Math.min(NEED_CAP, candidate);
                if (candidate > need[i]) {
                    need[i] = (int) candidate;
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return need;
    }

    /**
     * 阶段二：按额度分配。
     *
     * <p>处理顺序是<b>「先按从端分组、组内按 (主端优先级升序, 建链时间升序)」</b>，
     * 也就是从端逐位向慷慨的主端申请。分配本身迭代到不动点，因此中转站先从上游收到电、
     * 再往下游转发这件事会自行完成，与链路的排列无关。</p>
     */
    private static List<Transfer> allocate(int[] own, int[] need, List<Link> links) {
        int linkCount = links.size();
        if (linkCount == 0) return List.of();

        List<Integer> order = new ArrayList<>(linkCount);
        for (int i = 0; i < linkCount; i++) order.add(i);
        order.sort(Comparator
            // 先按从端分组：同一根从端的多条入边挨着处理，才能真正做到「逐位申请」
            .comparingInt((Integer i) -> links.get(i).nodeSlave())
            // 组内：优先级数值越小越先被申请
            .thenComparingInt(i -> links.get(i).priority())
            // 再按建链时间，优先级相同时先建先申请
            .thenComparingLong(i -> links.get(i).createdAt())
            .thenComparingInt(Integer::intValue));

        int nodeCount = own.length;
        int[] avail = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            avail[i] = (int) Math.min(Integer.MAX_VALUE, Math.max(0, own[i]));
        }
        int[] received = new int[nodeCount];
        int[] grant = new int[linkCount];

        int maxPasses = linkCount + MAX_PASSES_SLACK;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = false;
            for (int i : order) {
                Link link = links.get(i);
                int master = link.nodeMaster();
                int slave = link.nodeSlave();
                // 两端本来就在同一座电网里就没有输送的意义。
                if (master == slave) continue;

                int want = Math.max(0, need[slave] - received[slave]);
                if (want <= 0) continue;
                int give = Math.min(avail[master], want);
                if (give <= grant[i]) continue;

                int delta = give - grant[i];
                grant[i] = give;
                avail[master] -= delta;
                avail[slave] += delta;
                received[slave] += delta;
                changed = true;
            }
            if (!changed) break;
        }

        List<Transfer> transfers = new ArrayList<>();
        for (int i = 0; i < linkCount; i++) {
            if (grant[i] <= 0) continue;
            Link link = links.get(i);
            if (link.nodeMaster() == link.nodeSlave()) continue;
            transfers.add(new Transfer(i, link.nodeMaster(), link.nodeSlave(), grant[i]));
        }
        return transfers;
    }

    /**
     * 跨维度链路的<b>送达量</b>：按损耗系数折算。
     *
     * <p>损耗是「真的消失」的那部分功率：主端照常扣掉送出量 {@code amount}，
     * 从端只收到折算后的量，差额既不退还给主端、也不会跑到别处去。</p>
     *
     * <p>系数被夹在 0–1，送达量再夹回 {@code 0..amount}，因此本方法<b>永远不会放大</b>传输量：
     * 配置写错（负数、大于 1）只会退化成「无损」或「全损」，不会凭空发电。
     * 舍入用四舍五入——{@code amount} 是整数功率单位，1 的 50% 折算成 1 比 0 更不容易出现
     * 「明明有电却完全不过去」的错觉。</p>
     *
     * @param amount 主端送出的量，非正数一律返回 0
     * @param loss   损耗系数，0 表示无损
     */
    public static int deliveredAmount(int amount, double loss) {
        if (amount <= 0) return 0;
        if (!(loss > 0.0)) return amount; // 同时挡住 NaN
        double kept = 1.0 - Math.min(loss, 1.0);
        int delivered = (int) Math.round(amount * kept);
        return Math.max(0, Math.min(amount, delivered));
    }
}
