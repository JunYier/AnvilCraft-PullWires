package dev.anvilcraft.pullwires.power.link;

import java.util.List;

/**
 * {@link InterconnectSettlement} 的纯逻辑自检。
 *
 * <p>重点盯五件事：<b>单向性</b>（从端富余不回流）、优先级降序、同优先级按建立时间升序、
 * 局部守恒、不重复分配。</p>
 */
public final class InterconnectSettlementSelfCheck {
    private InterconnectSettlementSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    private static InterconnectSettlement.Link link(int index, int master, int slave, int priority, long createdAt) {
        return new InterconnectSettlement.Link(index, master, slave, priority, createdAt);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        int assertions = 0;
        int failures = 0;

        // --- 单链路基本传输 -------------------------------------------------
        int[] balances = {100, -30};
        List<InterconnectSettlement.Link> links = List.of(link(0, 0, 1, 7, 1));
        List<InterconnectSettlement.Transfer> transfers = InterconnectSettlement.settle(balances, links);
        assertions++;
        if (transfers.size() != 1 || transfers.getFirst().amount() != 30) {
            failures++;
            System.out.println("[pullwires] FAIL 单链路应传输缺口的全部 30");
        }
        assertions++;
        if (transfers.getFirst().fromNode() != 0 || transfers.getFirst().toNode() != 1) {
            failures++;
            System.out.println("[pullwires] FAIL 传输方向应为主端 → 从端");
        }

        // --- 结算不得修改调用方数组 ------------------------------------------
        assertions++;
        if (balances[0] != 100 || balances[1] != -30) {
            failures++;
            System.out.println("[pullwires] FAIL 结算不应修改传入的 balances");
        }

        // --- ★ 单向性：从端富余不得回流 ---------------------------------------
        // 同一条 master=0 → slave=1 的链路，把余量反过来：主端缺电、从端富余。
        assertions++;
        if (!InterconnectSettlement.settle(new int[] {-30, 100}, links).isEmpty()) {
            failures++;
            System.out.println("[pullwires] FAIL 从端的富余不得回流到主端（这是单向模型的核心）");
        }

        // --- ★ 互为主从：反向链路并存且互不干扰 --------------------------------
        List<InterconnectSettlement.Link> bothWays = List.of(
            link(0, 0, 1, 7, 1),
            link(1, 1, 0, 7, 2)
        );
        List<InterconnectSettlement.Transfer> bothWaysTransfers =
            InterconnectSettlement.settle(new int[] {100, -30}, bothWays);
        assertions++;
        if (bothWaysTransfers.size() != 1 || bothWaysTransfers.getFirst().fromNode() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 反向并存时只应有 0→1 成交");
        }

        // --- 两端都富余 / 都缺电：不传输 ------------------------------------
        assertions++;
        if (!InterconnectSettlement.settle(new int[] {50, 50}, List.of(link(0, 0, 1, 7, 1))).isEmpty()) {
            failures++;
            System.out.println("[pullwires] FAIL 两端都富余不应传输");
        }
        assertions++;
        if (!InterconnectSettlement.settle(new int[] {-50, -50}, List.of(link(0, 0, 1, 7, 1))).isEmpty()) {
            failures++;
            System.out.println("[pullwires] FAIL 两端都缺电不应传输（彼此救不了）");
        }

        // --- 优先级降序：高优先级先被满足 ------------------------------------
        int[] contested = {100, -80, -80};
        List<InterconnectSettlement.Link> contestedLinks = List.of(
            link(0, 0, 1, 3, 1),
            link(1, 0, 2, 12, 2)
        );
        List<InterconnectSettlement.Transfer> contestedTransfers =
            InterconnectSettlement.settle(contested, contestedLinks);
        int toLow = amountTo(contestedTransfers, 1);
        int toHigh = amountTo(contestedTransfers, 2);
        assertions++;
        if (toHigh != 80 || toLow != 20) {
            failures++;
            System.out.println("[pullwires] FAIL 高优先级应先被满足：期望 高=80/低=20，实际 高="
                               + toHigh + "/低=" + toLow);
        }

        // --- ★ 相同优先级按建立时间升序 ----------------------------------------
        int[] tied = {100, -80, -80};
        List<InterconnectSettlement.Link> tiedLinks = List.of(
            link(0, 0, 1, 7, 5),
            link(1, 0, 2, 7, 2)
        );
        List<InterconnectSettlement.Transfer> tiedTransfers = InterconnectSettlement.settle(tied, tiedLinks);
        assertions++;
        if (amountTo(tiedTransfers, 2) != 80 || amountTo(tiedTransfers, 1) != 20) {
            failures++;
            System.out.println("[pullwires] FAIL 同优先级应先满足更早建立的链路（createdAt=2 的那条）");
        }

        // --- 一份富余不得被重复分配 ------------------------------------------
        assertions++;
        if (toLow + toHigh > 100) {
            failures++;
            System.out.println("[pullwires] FAIL 总输出不得超过主端的富余 100");
        }

        // --- 局部守恒 ---------------------------------------------------------
        assertions++;
        if (!conserved(contestedTransfers)) {
            failures++;
            System.out.println("[pullwires] FAIL 每条链路应当局部守恒");
        }

        // --- 多主端补同一从端 --------------------------------------------------
        int[] fanIn = {100, 100, -90};
        List<InterconnectSettlement.Link> fanInLinks = List.of(
            link(0, 0, 2, 1, 1),
            link(1, 1, 2, 15, 2)
        );
        List<InterconnectSettlement.Transfer> fanInTransfers = InterconnectSettlement.settle(fanIn, fanInLinks);
        assertions++;
        if (amountTo(fanInTransfers, 2) != 90) {
            failures++;
            System.out.println("[pullwires] FAIL 从端应被补满缺口 90，实际 " + amountTo(fanInTransfers, 2));
        }
        assertions++;
        if (!conserved(fanInTransfers)) {
            failures++;
            System.out.println("[pullwires] FAIL 多链路场景应当局部守恒");
        }

        // --- 不支持链式传递 ---------------------------------------------------
        int[] chain = {100, -50, -50};
        List<InterconnectSettlement.Link> chainLinks = List.of(
            link(0, 0, 1, 7, 1),
            link(1, 1, 2, 7, 2)
        );
        List<InterconnectSettlement.Transfer> chainTransfers = InterconnectSettlement.settle(chain, chainLinks);
        assertions++;
        if (amountTo(chainTransfers, 2) != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 中间网络被补平后不应再向下一跳传递");
        }

        // --- 确定性 -----------------------------------------------------------
        assertions++;
        if (!InterconnectSettlement.settle(contested, contestedLinks)
            .equals(InterconnectSettlement.settle(contested, contestedLinks))) {
            failures++;
            System.out.println("[pullwires] FAIL 同样输入应得到同样输出");
        }

        // --- 自环链路必须被忽略 -----------------------------------------------
        assertions++;
        if (!InterconnectSettlement.settle(new int[] {-50}, List.of(link(0, 0, 0, 7, 1))).isEmpty()) {
            failures++;
            System.out.println("[pullwires] FAIL 自环链路应被忽略");
        }

        if (failures == 0) {
            System.out.println("[pullwires] InterconnectSettlement ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] InterconnectSettlement FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    private static int amountTo(List<InterconnectSettlement.Transfer> transfers, int node) {
        int total = 0;
        for (InterconnectSettlement.Transfer transfer : transfers) {
            if (transfer.toNode() == node) total += transfer.amount();
        }
        return total;
    }

    private static boolean conserved(List<InterconnectSettlement.Transfer> transfers) {
        for (InterconnectSettlement.Transfer transfer : transfers) {
            if (transfer.amount() <= 0) return false;
            if (transfer.fromNode() == transfer.toNode()) return false;
        }
        return true;
    }
}
