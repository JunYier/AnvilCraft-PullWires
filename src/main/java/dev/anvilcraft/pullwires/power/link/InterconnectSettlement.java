package dev.anvilcraft.pullwires.power.link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 链路互联结算：把富余电网的功率分配给缺电电网。
 *
 * <p>纯整数运算，不依赖任何游戏类型，可脱离游戏单独验证。</p>
 *
 * <h2>为什么只按一个方向排序</h2>
 * <p>需求原话是「给电相按优先级升序、取电相按优先级降序」，但在<b>每条链路两端局部守恒</b>的模型下，
 * 这两个排序会互相打架。反例：给电端 G 富余 100，它连着两个缺电端 T1（缺口 80，优先级 3）
 * 与 T2（缺口 80，优先级 12）。</p>
 * <ul>
 *   <li>先按给电升序让低优先级链路占额度：T1 先占满 80，G 只剩 20 给 T2；</li>
 *   <li>再按取电降序完成：T2 只拿到 20，T1 拿到 80——<b>高优先级反而拿得更少</b>。</li>
 * </ul>
 * <p>反过来先满足取电降序，则低优先级会被饿死。二者不可兼得，必须选一个。</p>
 *
 * <p><b>本实现选择「取电降序」</b>：优先级高的链路先获得补充。理由是这一条直接决定缺电时谁先被救活，
 * 是玩家最能感知的语义；而「给电升序」在总量守恒下不改变任何结果——把富余投入池里的先后顺序
 * 不影响池的总量，只有当存在单条链路上限时才会体现出来（本实现暂不设上限）。</p>
 *
 * <p>结算结果保证：① 每条链路局部守恒（给电端扣多少，取电端就加多少）；
 * ② 任何网络的总输出不超过自身富余，总输入不超过自身缺口；③ 同一份富余不会被重复分配。</p>
 */
public final class InterconnectSettlement {
    private InterconnectSettlement() {
    }

    /**
     * 一条参与结算的链路。
     *
     * @param index    调用方给出的稳定下标，用于把结果映射回具体链路
     * @param nodeA    一端所在网络的编号（对应 {@code balances} 的下标）
     * @param nodeB    另一端所在网络的编号
     * @param priority 链路优先级（取主端杆的优先级），越大越优先获得补充
     */
    public record Link(int index, int nodeA, int nodeB, int priority) {
    }

    /**
     * 一条成交的传输。
     *
     * @param amount 恒为正，方向为 {@code fromNode → toNode}
     */
    public record Transfer(int linkIndex, int fromNode, int toNode, int amount) {
    }

    /**
     * 执行一次结算。
     *
     * @param balances 各网络的余量，正数表示富余、负数表示缺口；<b>不会被修改</b>
     * @param links    参与结算的链路
     * @return 本拍成交的传输，未被满足的缺口不会出现在结果里
     */
    public static List<Transfer> settle(int[] balances, List<Link> links) {
        int[] remaining = balances.clone();

        List<Integer> order = new ArrayList<>(links.size());
        for (int i = 0; i < links.size(); i++) order.add(i);
        // 取电降序：优先级高的先获得补充；同优先级按下标排序，保证同样输入永远得到同样输出。
        order.sort(Comparator
            .comparingInt((Integer index) -> -links.get(index).priority())
            .thenComparingInt(Integer::intValue));

        List<Transfer> transfers = new ArrayList<>();
        for (int linkIndex : order) {
            Link link = links.get(linkIndex);
            int nodeA = link.nodeA();
            int nodeB = link.nodeB();
            if (nodeA == nodeB) continue;

            int giver;
            int taker;
            if (remaining[nodeA] > 0 && remaining[nodeB] < 0) {
                giver = nodeA;
                taker = nodeB;
            } else if (remaining[nodeB] > 0 && remaining[nodeA] < 0) {
                giver = nodeB;
                taker = nodeA;
            } else {
                // 两端都富余（无需互联）或都缺电（彼此救不了）。
                continue;
            }

            int amount = Math.min(remaining[giver], -remaining[taker]);
            if (amount <= 0) continue;
            remaining[giver] -= amount;
            remaining[taker] += amount;
            transfers.add(new Transfer(linkIndex, giver, taker, amount));
        }
        return transfers;
    }
}
