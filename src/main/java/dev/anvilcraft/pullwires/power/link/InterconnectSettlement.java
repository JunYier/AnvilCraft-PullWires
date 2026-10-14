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
 * <p>每条链路是有向边 {@code master → slave}：<b>只有主端能送电给从端</b>。
 * 从端的富余不会回流，因此不再存在「谁富余谁送出」的对称性。</p>
 *
 * <p>这带来两个直接后果：</p>
 * <ul>
 *   <li><b>不再有排序方向的矛盾</b>：旧实现里「给电升序 vs 取电降序」互相打架
 *       （给电端只有一个排序维度、取电端却有多个），选了取电降序就必然饿死低优先级的取电端。
 *       单向化后每条链路只有一个决策者——主端，排序键退化为
 *       {@code (优先级降序, 建立时间升序)}。</li>
 *   <li><b>允许互为主从</b>：{@code A → B} 与 {@code B → A} 是两条独立的边，互不干扰。
 *       由于「富余」与「亏电」不可能同时成立，同一拍里两者不会互相抵消或震荡。</li>
 * </ul>
 *
 * <h2>排序键</h2>
 * <ul>
 *   <li><b>优先级降序</b>：主端优先级高的链路先获得输送额度。</li>
 *   <li><b>建立时间升序</b>：优先级相同时，先建立的链路先被满足。</li>
 *   <li><b>下标升序</b>：前两者都相同时的最终决胜，保证同样输入永远得到同样输出。</li>
 * </ul>
 *
 * <p>结算结果保证：① 每条链路局部守恒（主端扣多少，从端就加多少）；
 * ② 主端的总输出不超过自身富余，从端的总输入不超过自身缺口；③ 同一份富余不会被重复分配。</p>
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
     * @param priority   主端杆的结算优先级，越大越优先
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

    /**
     * 执行一次结算。
     *
     * @param balances 各网络的余量，正数表示富余、负数表示缺口；<b>不会被修改</b>
     * @param links    参与结算的有向链路
     * @return 本拍成交的传输，未被满足的缺口不会出现在结果里
     */
    public static List<Transfer> settle(int[] balances, List<Link> links) {
        int[] remaining = balances.clone();

        List<Integer> order = new ArrayList<>(links.size());
        for (int i = 0; i < links.size(); i++) order.add(i);
        order.sort(Comparator
            .comparingInt((Integer index) -> -links.get(index).priority())
            .thenComparingLong(index -> links.get(index).createdAt())
            .thenComparingInt(Integer::intValue));

        List<Transfer> transfers = new ArrayList<>();
        for (int linkIndex : order) {
            Link link = links.get(linkIndex);
            int master = link.nodeMaster();
            int slave = link.nodeSlave();
            // 两端本来就在同一座电网里就没有输送的意义。
            if (master == slave) continue;
            // 单向：主端必须有富余，从端必须有缺口，缺一不可。
            if (remaining[master] <= 0 || remaining[slave] >= 0) continue;

            int amount = Math.min(remaining[master], -remaining[slave]);
            if (amount <= 0) continue;
            remaining[master] -= amount;
            remaining[slave] += amount;
            transfers.add(new Transfer(linkIndex, master, slave, amount));
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
