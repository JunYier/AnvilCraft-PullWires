package dev.anvilcraft.pullwires.power.link;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link ReconnectPolicy} 的纯逻辑自检。
 *
 * <p>钉死三件事：主/副因素的方向开关按玩家设定生效、时间键固定升序且只在两个因素都相等时介入、
 * 以及「批准」用的是平均量而不是瞬时量。</p>
 *
 * <p>V1.12.12 起再加一组：配置页上那四个开关背后的纯函数——三开关到策略的映射
 * （{@code ofFlags}）、存档默认与单杆覆盖的解析（{@code resolve}）、
 * 以及「换主因素」与「翻方向」互不牵连。</p>
 *
 * <p>V1.12.13 再去掉「覆盖开关」之后，{@code resolve} 只是补齐之前的兜底读法；
 * 补齐本身（{@code materialize}）也在这里断言：空 → 补上存档默认、非空 → 原样保留，
 * 两者合起来给出幂等性。</p>
 */
public final class ReconnectPolicySelfCheck {
    private static int assertions;
    private static int failures;

    private ReconnectPolicySelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        assertions = 0;
        failures = 0;


        // 两个申请：a 更慷慨（优先级 3 < 9）、需求更小（10 < 50）、建立更早（1 < 2）
        ReconnectPolicy.Request generous = new ReconnectPolicy.Request(3, 10, 1);
        ReconnectPolicy.Request needy = new ReconnectPolicy.Request(9, 50, 2);

        // 优先级升序：数值小的先
        ReconnectPolicy byPriorityAsc = new ReconnectPolicy(ReconnectPolicy.Factor.PRIORITY, true, true);
        expect("优先级升序时数值小的先", byPriorityAsc.compare(generous, needy) < 0);
        // 优先级降序：反过来
        ReconnectPolicy byPriorityDesc = new ReconnectPolicy(ReconnectPolicy.Factor.PRIORITY, false, true);
        expect("优先级降序时数值大的先", byPriorityDesc.compare(generous, needy) > 0);

        // 主因素切换成需求：a 需求 10 < b 需求 50
        ReconnectPolicy byDemand = new ReconnectPolicy(ReconnectPolicy.Factor.DEMAND, true, true);
        expect("需求优先时缺口小的先", byDemand.compare(generous, needy) < 0);
        expect("副因素取主因素之外的那一个",
            byDemand.secondary() == ReconnectPolicy.Factor.PRIORITY
            && byPriorityAsc.secondary() == ReconnectPolicy.Factor.DEMAND);

        // 主因素相等时才轮到副因素：同样优先级下，按（副因素=需求）排
        ReconnectPolicy.Request samePriorityEarly = new ReconnectPolicy.Request(7, 40, 5);
        ReconnectPolicy.Request samePriorityLate = new ReconnectPolicy.Request(7, 10, 6);
        expect("主因素相等时副因素生效", byPriorityAsc.compare(samePriorityLate, samePriorityEarly) < 0);
        // 副因素降序则反过来
        ReconnectPolicy byPriorityAscDemandDesc = new ReconnectPolicy(ReconnectPolicy.Factor.PRIORITY, true, false);
        expect("副因素降序生效", byPriorityAscDemandDesc.compare(samePriorityLate, samePriorityEarly) > 0);

        // 两个因素都相等时，时间固定升序：先建立的先被考虑
        ReconnectPolicy.Request twinEarly = new ReconnectPolicy.Request(7, 20, 3);
        ReconnectPolicy.Request twinLate = new ReconnectPolicy.Request(7, 20, 8);
        expect("两因素相等时时间升序", byPriorityAsc.compare(twinEarly, twinLate) < 0);
        expect("时间键不受方向开关影响",
            byPriorityAscDemandDesc.compare(twinEarly, twinLate) < 0
            && ReconnectPolicy.TIME_IS_ASCENDING == 1L);

        // 排序的稳定性：同一次比较重复调用结果一致（排序依赖它才不会抖）
        expect("比较可重复", byPriorityAsc.compare(generous, needy) == byPriorityAsc.compare(generous, needy));

        // 真正排一遍：优先级升序下，慷慨者在前
        List<ReconnectPolicy.Request> requests = new ArrayList<>(List.of(needy, generous, twinLate));
        requests.sort(byPriorityAsc);
        expect("排序后最慷慨的排第一位", requests.getFirst() == generous);
        expect("时间最早的排在同因素者之前", requests.get(1) == twinLate);

        // 批准判据：用平均量，且需求必须为正
        expect("平均富余够则批准", ReconnectPolicy.approve(100, 60));
        expect("平均富余恰好相等也批准", ReconnectPolicy.approve(60, 60));
        expect("平均富余不足则不批", !ReconnectPolicy.approve(59, 60));
        expect("没有需求不批（不必凭空送电）", !ReconnectPolicy.approve(100, 0));
        expect("负需求不批", !ReconnectPolicy.approve(100, -5));

        // ---------------------------------------------------------------- 配置页的四个开关
        // 存档级默认与「单杆覆盖」都由这几个纯函数拼/解析，页面上按一下按钮就是调它们其中一个。

        // 三个开关 → 策略：这是配置项与网络包唯一的那条映射
        expect("三个开关拼出需求优先", ReconnectPolicy.ofFlags(true, true, true).primary() == ReconnectPolicy.Factor.DEMAND);
        expect("三个开关拼出优先级优先", ReconnectPolicy.ofFlags(false, false, true).primary() == ReconnectPolicy.Factor.PRIORITY);
        expect("默认组合就是 DEFAULT",
            ReconnectPolicy.ofFlags(false, true, true).equals(ReconnectPolicy.DEFAULT));
        expect("demandFirst 与主因素一致",
            !ReconnectPolicy.ofFlags(false, true, true).demandFirst()
            && ReconnectPolicy.ofFlags(true, true, true).demandFirst());

        // 覆盖与存档默认：空覆盖一律退回默认，有覆盖一律用覆盖
        expect("没有覆盖时用存档默认",
            ReconnectPolicy.resolve(Optional.empty(), byDemand).equals(byDemand));
        expect("有覆盖时用覆盖",
            ReconnectPolicy.resolve(Optional.of(byPriorityDesc), byDemand).equals(byPriorityDesc));

        // ---------------------------------------------------------------- 进世界补齐（V1.12.13）
        // 页面上不再有「覆盖 / 跟随默认」开关，剩下的只是「这根杆还没被配置过」这个状态：
        // 结算时把空的那一份物化成存档默认快照，已经有的原样不动。

        Optional<ReconnectPolicy> backfilled = ReconnectPolicy.materialize(Optional.empty(), byDemand);
        expect("尚未配置的杆被补上一份存档默认", backfilled.equals(Optional.of(byDemand)));
        expect("已经配置的杆原样保留，不被存档默认盖掉",
            ReconnectPolicy.materialize(Optional.of(byPriorityDesc), byDemand).equals(Optional.of(byPriorityDesc)));
        expect("补齐一次之后再补一次结果不变（幂等：重复执行不产生第二次改动）",
            ReconnectPolicy.materialize(backfilled, byDemand).equals(backfilled));
        expect("已配置的杆碰上另一份存档默认也仍是自己那一份",
            ReconnectPolicy.materialize(Optional.of(byPriorityDesc), byPriorityAsc)
                .equals(Optional.of(byPriorityDesc)));
        expect("补齐写入的那一份与后续 resolve 读到的是同一个值",
            ReconnectPolicy.resolve(backfilled, byPriorityDesc).equals(byDemand)
            && ReconnectPolicy.resolve(
                ReconnectPolicy.materialize(Optional.of(byPriorityDesc), byDemand), byPriorityAsc
            ).equals(byPriorityDesc));

        // 切换主因素：只换因素，两个方向开关必须原样保留
        ReconnectPolicy toggled = byPriorityAsc.toggledPrimary();
        expect("切换主因素后因素变了", toggled.primary() == ReconnectPolicy.Factor.DEMAND);
        expect("切换主因素不动主因素方向", toggled.primaryAscending() == byPriorityAsc.primaryAscending());
        expect("切换主因素不动副因素方向", toggled.secondaryAscending() == byPriorityAsc.secondaryAscending());
        expect("连按两次回到原处", toggled.toggledPrimary().equals(byPriorityAsc));

        // 切换方向：只翻自己那一个
        ReconnectPolicy primaryFlipped = byPriorityAsc.toggledPrimaryAscending();
        expect("翻转主因素方向", !primaryFlipped.primaryAscending() && primaryFlipped.secondaryAscending());
        ReconnectPolicy secondaryFlipped = byPriorityAsc.toggledSecondaryAscending();
        expect("翻转副因素方向", secondaryFlipped.primaryAscending() && !secondaryFlipped.secondaryAscending());
        expect("翻转后才真的反过来", primaryFlipped.compare(generous, needy) > 0);

        // 主因素换成需求之后：「主因素方向」管的是需求，副因素自动接过优先级
        // 这一对申请的需求相同、优先级不同，因此只有副因素键能分出先后。
        ReconnectPolicy.Request tieDemandGenerous = new ReconnectPolicy.Request(3, 20, 1);
        ReconnectPolicy.Request tieDemandNeedy = new ReconnectPolicy.Request(9, 20, 2);
        ReconnectPolicy demandToggled = byPriorityAsc.toggledPrimary();
        expect("换成需求后主因素方向仍作用于需求", demandToggled.compare(generous, needy) < 0);
        expect("换成需求后副因素变成优先级", demandToggled.secondary() == ReconnectPolicy.Factor.PRIORITY);
        expect("换成需求后副因素方向仍生效",
            demandToggled.compare(tieDemandGenerous, tieDemandNeedy) < 0
            && demandToggled.toggledSecondaryAscending().compare(tieDemandGenerous, tieDemandNeedy) > 0);
        expect("换因素不影响时间键",
            demandToggled.compare(twinEarly, twinLate) < 0
            && demandToggled.toggledPrimaryAscending().compare(twinEarly, twinLate) < 0);

        if (failures == 0) {
            System.out.println("[pullwires] ReconnectPolicy ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] ReconnectPolicy FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    private static void expect(String name, boolean condition) {
        assertions++;
        if (condition) return;
        failures++;
        System.out.println("[pullwires] FAIL " + name);
    }
}
