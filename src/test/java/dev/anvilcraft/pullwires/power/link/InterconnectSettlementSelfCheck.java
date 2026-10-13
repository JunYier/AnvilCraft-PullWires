package dev.anvilcraft.pullwires.power.link;

import java.util.List;

/**
 * {@link InterconnectSettlement} 的纯逻辑自检。
 *
 * <p>重点盯三件事：优先级方向、局部守恒、不重复分配。</p>
 */
public final class InterconnectSettlementSelfCheck {
    private InterconnectSettlementSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        int assertions = 0;
        int failures = 0;

        // --- 单链路基本传输 -------------------------------------------------
        int[] balances = {100, -30};
        List<InterconnectSettlement.Link> links = List.of(new InterconnectSettlement.Link(0, 0, 1, 7));
        List<InterconnectSettlement.Transfer> transfers = InterconnectSettlement.settle(balances, links);
        assertions++;
        if (transfers.size() != 1 || transfers.getFirst().amount() != 30) {
            failures++;
            System.out.println("[pullwires] FAIL 单链路应传输缺口的全部 30");
        }
        assertions++;
        if (transfers.getFirst().fromNode() != 0 || transfers.getFirst().toNode() != 1) {
            failures++;
            System.out.println("[pullwires] FAIL 传输方向应为富余端 → 缺电端");
        }

        // --- 结算不得修改调用方数组 ------------------------------------------
        assertions++;
        if (balances[0] != 100 || balances[1] != -30) {
            failures++;
            System.out.println("[pullwires] FAIL 结算不应修改传入的 balances");
        }

        // --- 两端都富余 / 都缺电：不传输 ------------------------------------
        assertions++;
        if (!InterconnectSettlement.settle(new int[] {50, 50}, List.of(new InterconnectSettlement.Link(0, 0, 1, 7)))
            .isEmpty()) {
            failures++;
            System.out.println("[pullwires] FAIL 两端都富余不应传输");
        }
        assertions++;
        if (!InterconnectSettlement.settle(new int[] {-50, -50}, List.of(new InterconnectSettlement.Link(0, 0, 1, 7)))
            .isEmpty()) {
            failures++;
            System.out.println("[pullwires] FAIL 两端都缺电不应传输（彼此救不了）");
        }

        // --- 优先级方向：高优先级先被满足 ------------------------------------
        // G 富余 100，T1 缺口 80（优先级 3），T2 缺口 80（优先级 12）。
        int[] contested = {100, -80, -80};
        List<InterconnectSettlement.Link> contestedLinks = List.of(
            new InterconnectSettlement.Link(0, 0, 1, 3),
            new InterconnectSettlement.Link(1, 0, 2, 12)
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

        // --- 一份富余不得被重复分配 ------------------------------------------
        assertions++;
        if (toLow + toHigh > 100) {
            failures++;
            System.out.println("[pullwires] FAIL 总输出不得超过给电端的富余 100");
        }

        // --- 局部守恒：给多少就收多少 ----------------------------------------
        assertions++;
        if (!conserved(contestedTransfers)) {
            failures++;
            System.out.println("[pullwires] FAIL 每条链路应当局部守恒");
        }

        // --- 多给电端补同一缺电端 --------------------------------------------
        // E 总缺口 120，两个各有 100 富余的网络都连到 E。
        int[] fanIn = {100, 100, -90};
        List<InterconnectSettlement.Link> fanInLinks = List.of(
            new InterconnectSettlement.Link(0, 0, 2, 1),
            new InterconnectSettlement.Link(1, 1, 2, 15)
        );
        List<InterconnectSettlement.Transfer> fanInTransfers = InterconnectSettlement.settle(fanIn, fanInLinks);
        assertions++;
        if (amountTo(fanInTransfers, 2) != 90) {
            failures++;
            System.out.println("[pullwires] FAIL 缺电端应被补满缺口 90，实际 " + amountTo(fanInTransfers, 2));
        }
        assertions++;
        if (!conserved(fanInTransfers)) {
            failures++;
            System.out.println("[pullwires] FAIL 多链路场景应当局部守恒");
        }

        // --- 不支持链式传递 ---------------------------------------------------
        // A 富余 100、B 缺电 50 且还连着缺电的 C：B 被补平后，C 拿不到任何东西。
        // 这是「每条链路局部守恒」模型的直接推论，需要显式记录下来，避免以后被当成 bug。
        int[] chain = {100, -50, -50};
        List<InterconnectSettlement.Link> chainLinks = List.of(
            new InterconnectSettlement.Link(0, 0, 1, 7),
            new InterconnectSettlement.Link(1, 1, 2, 7)
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
        if (!InterconnectSettlement.settle(new int[] {-50}, List.of(new InterconnectSettlement.Link(0, 0, 0, 7)))
            .isEmpty()) {
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
