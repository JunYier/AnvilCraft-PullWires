package dev.anvilcraft.pullwires.power.link;

import java.util.List;

/**
 * {@link InterconnectSettlement} 的纯逻辑自检。
 *
 * <p>核心要钉死两件事：</p>
 * <ol>
 *   <li><b>多跳中继</b>：中转站自己没有用电器（自有余额 0）时，上游给它的电必须能继续转给下游；</li>
 *   <li><b>需求基准必须是自有余额</b>：否则上一拍给出的电会抵消这一拍的需求，成交退化成隔一拍一次。</li>
 * </ol>
 */
public final class InterconnectSettlementSelfCheck {
    private static int assertions;
    private static int failures;

    private InterconnectSettlementSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        assertions = 0;
        failures = 0;

        // --- 单跳：主端富余、从端缺口 ----------------------------------------
        List<InterconnectSettlement.Transfer> one = InterconnectSettlement.settle(
            new int[] {100, -30}, List.of(link(0, 0, 1, 7, 1))
        );
        expect("单跳应成交", one.size() == 1);
        expect("单跳量取从端缺口", one.getFirst().sent() == 30);
        expect("方向必须是主端→从端", one.getFirst().fromNode() == 0 && one.getFirst().toNode() == 1);

        // --- 主端富余不足：只给得起多少给多少 --------------------------------
        List<InterconnectSettlement.Transfer> limited = InterconnectSettlement.settle(
            new int[] {10, -50}, List.of(link(0, 0, 1, 7, 1))
        );
        expect("主端富余不足时按上限给", limited.size() == 1 && limited.getFirst().sent() == 10);

        // --- ★ 多跳中继：中转站自己没有任何用电器 ----------------------------
        // 0（富余 100）→ 1（自有 0，纯中继）→ 2（缺口 50）
        // 早期「一趟遍历按缺口取量」的实现在这里必然失败：1 没有缺口，0→1 会被直接跳过。
        List<InterconnectSettlement.Transfer> relay = InterconnectSettlement.settle(
            new int[] {100, 0, -50},
            List.of(link(0, 0, 1, 7, 1), link(1, 1, 2, 7, 2))
        );
        expect("多跳中继两段都要成交", relay.size() == 2);
        expect("第一段把电送到中转站", amountOf(relay, 0) == 50);
        expect("第二段从中转站转到下游", amountOf(relay, 1) == 50);

        // --- 主端富余不足时，下游只能拿到转手得到的那部分 ---------------------
        List<InterconnectSettlement.Transfer> relayLimited = InterconnectSettlement.settle(
            new int[] {20, 0, -50},
            List.of(link(0, 0, 1, 7, 1), link(1, 1, 2, 7, 2))
        );
        expect("中继链受最上游产能限制", amountOf(relayLimited, 0) == 20 && amountOf(relayLimited, 1) == 20);
        // 中继不放大：下游拿到的恰好等于上游交给它的。注意不能把各段求和当作「总送出」——
        // 同一份电经过两段会被计两次，那是计数口径问题，不是能量问题。
        expect("中继不放大", amountOf(relayLimited, 1) == amountOf(relayLimited, 0));

        // --- ★ 从端按 (优先级数值升序, 建链时间升序) 逐位申请 ----------------
        // 从端 2 缺口 150，两位主端各有 100。优先级 3 的那位必须被先申请。
        List<InterconnectSettlement.Transfer> pick = InterconnectSettlement.settle(
            new int[] {100, 100, -150},
            List.of(link(0, 0, 2, 9, 1), link(1, 1, 2, 3, 2))
        );
        expect("先向优先级数值最小的主端申请", amountOf(pick, 1) == 100);
        expect("不够时再向次位申请", amountOf(pick, 0) == 50);

        // 优先级相同时按建链时间：先建的先被申请
        List<InterconnectSettlement.Transfer> pickByAge = InterconnectSettlement.settle(
            new int[] {100, 100, -150},
            List.of(link(0, 0, 2, 7, 50), link(1, 1, 2, 7, 10))
        );
        expect("优先级相同时先建的先被申请", amountOf(pickByAge, 1) == 100 && amountOf(pickByAge, 0) == 50);

        // --- 不传输的情形 ----------------------------------------------------
        expect("两端都不缺电不应传输", InterconnectSettlement.settle(
            new int[] {50, 0}, List.of(link(0, 0, 1, 7, 1))
        ).isEmpty());
        expect("两端都缺电不应传输", InterconnectSettlement.settle(
            new int[] {-50, -50}, List.of(link(0, 0, 1, 7, 1))
        ).isEmpty());
        expect("从端富余不得回流主端", InterconnectSettlement.settle(
            new int[] {-30, 100}, List.of(link(0, 0, 1, 7, 1))
        ).isEmpty());
        expect("同网自连应跳过", InterconnectSettlement.settle(
            new int[] {100, -30}, List.of(link(0, 0, 0, 7, 1))
        ).isEmpty());

        // --- 结算不得修改传入的 balances ------------------------------------
        int[] source = {100, -30};
        InterconnectSettlement.settle(source, List.of(link(0, 0, 1, 7, 1)));
        expect("结算不得修改传入数组", source[0] == 100 && source[1] == -30);

        // --- ★ 振荡回归：需求基准必须是自有余额 ------------------------------
        // 取量公式要求「从端有缺口」。若把上一拍注入的增量算进从端余额，
        // 上一拍给出的电就把这一拍的需求抵消掉，成交退化成隔一拍一次。
        int injected = 0;
        int bookedTicks = 0;
        for (int tick = 0; tick < 6; tick++) {
            List<InterconnectSettlement.Transfer> result = InterconnectSettlement.settle(
                new int[] {100, -20 + injected}, List.of(link(0, 0, 1, 7, 1))
            );
            injected = result.isEmpty() ? 0 : result.getFirst().sent();
            if (!result.isEmpty()) bookedTicks++;
        }
        expect("账面基准恰好隔一拍成交一次（6 拍 3 次）", bookedTicks == 3);

        int ownTicks = 0;
        for (int tick = 0; tick < 6; tick++) {
            if (!InterconnectSettlement.settle(
                new int[] {100, -20}, List.of(link(0, 0, 1, 7, 1))
            ).isEmpty()) ownTicks++;
        }
        expect("自有基准每拍都成交（6 拍 6 次）", ownTicks == 6);

        // --- ★ 损耗向上折算（V1.12.15）----------------------------------------
        // 语义：结算给出的量是「从端希望收到多少」；主端要付出 need/(1-loss) 才能让从端真的收到 need。
        expect("无损时送出量等于需求", InterconnectSettlement.grossUp(256, 0.0) == 256);
        expect("20% 损耗：要送 256 需付出 320", InterconnectSettlement.grossUp(256, 0.2) == 320);
        expect("向上取整而不是四舍五入", InterconnectSettlement.grossUp(1, 0.2) == 2);
        expect("全损时不付出", InterconnectSettlement.grossUp(100, 1.0) == 0);
        expect("非正需求返回 0", InterconnectSettlement.grossUp(0, 0.2) == 0);

        // 关键不变量：按折算后的送出量真正送达，必须覆盖住需求（否则从端永远差一截 → 主端被慢慢抽干）
        for (double loss : new double[] {0.05, 0.1, 0.2, 0.25, 0.5}) {
            for (int need : new int[] {1, 7, 64, 256, 1000}) {
                int sent = InterconnectSettlement.grossUp(need, loss);
                int delivered = InterconnectSettlement.deliveredAmount(sent, loss);
                expect("折算往返覆盖需求 loss=" + loss + " need=" + need, delivered >= need);
            }
        }
        // 反例守护：旧做法（把需求直接当送出量）在 20% 损耗下只能送到 205，覆盖不了 256——
        // 这正是「主端电很多却十几秒后被断路」的根因。
        expect("旧做法确实覆盖不了需求", InterconnectSettlement.deliveredAmount(256, 0.2) == 205);
        // --- ★ 损耗结算（V1.12.16）：主端付出与从端收到必须分开记账 ---------------
        // 从端需要 256，损耗 20%：主端应付 320，从端应收到 256。
        List<InterconnectSettlement.Transfer> lossy = InterconnectSettlement.settle(
            new int[] {320, -256}, List.of(lossyLink(0, 0, 1, 7, 1, 0.2))
        );
        expect("有损耗时主端付出向上折算", amountOf(lossy, 0) == 320);
        expect("有损耗时从端恰好收到需求", receivedOf(lossy, 0) == 256);

        // 主端只有 256（付不起 320）：付 256、送达 205，且**不会**因此把它算成亏电。
        List<InterconnectSettlement.Transfer> short1 = InterconnectSettlement.settle(
            new int[] {256, -256}, List.of(lossyLink(0, 0, 1, 7, 1, 0.2))
        );
        expect("付不起时按额度封顶", amountOf(short1, 0) == 256);
        expect("付不起时送达按损耗折算", receivedOf(short1, 0) == 205);
        expect("付不起也不会超支", amountOf(short1, 0) <= 256);

        // 多跳 + 损耗：A →(20%) B →(20%) C，C 需要 100，则 B 需收到 125、A 需付出 157。
        List<InterconnectSettlement.Transfer> chain = InterconnectSettlement.settle(
            new int[] {1000, 0, -100},
            List.of(lossyLink(0, 0, 1, 7, 1, 0.2), lossyLink(1, 1, 2, 7, 2, 0.2))
        );
        expect("多跳损耗：第一段付出 157", amountOf(chain, 0) == 157);
        expect("多跳损耗：第一段送达 125", receivedOf(chain, 0) == 125);
        expect("多跳损耗：第二段送达 100", receivedOf(chain, 1) == 100);
        if (failures == 0) {
            System.out.println("[pullwires] InterconnectSettlement ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] InterconnectSettlement FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    private static InterconnectSettlement.Link link(int index, int master, int slave, int priority, long createdAt) {
        return new InterconnectSettlement.Link(index, master, slave, priority, createdAt, 0.0);
    }

    private static InterconnectSettlement.Link lossyLink(
        int index, int master, int slave, int priority, long createdAt, double loss
    ) {
        return new InterconnectSettlement.Link(index, master, slave, priority, createdAt, loss);
    }
    /** 按调用方给的稳定下标取这一条的成交量。 */
    /** 按链路的稳定下标取这一条的送达量。 */
    private static int receivedOf(List<InterconnectSettlement.Transfer> transfers, int linkIndex) {
        for (InterconnectSettlement.Transfer transfer : transfers) {
            if (transfer.linkIndex() == linkIndex) return transfer.received();
        }
        return 0;
    }

    private static int amountOf(List<InterconnectSettlement.Transfer> transfers, int linkIndex) {
        for (InterconnectSettlement.Transfer transfer : transfers) {
            if (transfer.linkIndex() == linkIndex) return transfer.sent();
        }
        return 0;
    }


    private static void expect(String name, boolean condition) {
        assertions++;
        if (condition) return;
        failures++;
        System.out.println("[pullwires] FAIL " + name);
    }
}
