package dev.anvilcraft.pullwires.power.link;

/**
 * {@link ManualLinkRule} 的纯逻辑自检。
 *
 * <p>这条规则客户端与服务端共用，一边算「引导带什么颜色」、一边算「放行还是拒绝」，
 * 所以边界必须钉死，否则会出现「看着在范围内、放下却被拒」。</p>
 */
public final class ManualLinkRuleSelfCheck {
    private static final int RANGE = 64;
    private static final double MULTIPLIER = 2.0;

    private static int assertions;
    private static int failures;

    private ManualLinkRuleSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        assertions = 0;
        failures = 0;

        expect("近距离应在范围内", ManualLinkFeedback.IN_RANGE, 0);
        expect("范围中段应在范围内", ManualLinkFeedback.IN_RANGE, 32);
        expect("恰好等于范围应算在范围内（闭区间）", ManualLinkFeedback.IN_RANGE, RANGE);
        expect("刚超出范围应算超范围", ManualLinkFeedback.OUT_OF_RANGE, RANGE + 0.1);
        expect("放弃距离之内仍是超范围", ManualLinkFeedback.OUT_OF_RANGE, RANGE * MULTIPLIER);
        expect("刚超过放弃距离应放弃", ManualLinkFeedback.CANCEL, RANGE * MULTIPLIER + 0.1);
        expect("远距离应放弃", ManualLinkFeedback.CANCEL, 10_000);

        // 倍数可配置：设为 1 时超范围即放弃，不存在中间态。
        expectWithMultiplier("倍数 1.0 时恰好等于范围仍在范围内", ManualLinkFeedback.IN_RANGE, RANGE, 1.0);
        expectWithMultiplier("倍数 1.0 时刚超出即放弃", ManualLinkFeedback.CANCEL, RANGE + 0.1, 1.0);

        // Boss 条：0 → 3/4 走完连接范围，剩下的 1/4 走「超出到断开」，两段各自线性。
        expectProgress("起点为 0", 0.0F, 0);
        expectProgress("连接范围一半 = 3/8", 0.375F, RANGE * 0.5);
        expectProgress("恰好到连接范围 = 3/4", ManualLinkRule.GREEN_SEGMENT_END, RANGE);
        expectProgress("超出段中点 = 7/8", 0.875F, RANGE * 1.5);
        expectProgress("恰好到放弃距离 = 满", 1.0F, RANGE * MULTIPLIER);
        expectProgress("超过放弃距离仍夹在满", 1.0F, 10_000);

        // 两段斜率必须不同：同样走半个连接范围，绿色段占 3/8 条、红色段只占 1/8 条。
        // 也就是说绿色段每格走得更快——这正是「两段之间不线性」的含义。
        float greenStep = ManualLinkRule.barProgress(RANGE * 0.5, RANGE, MULTIPLIER)
                          - ManualLinkRule.barProgress(0, RANGE, MULTIPLIER);
        float redStep = ManualLinkRule.barProgress(RANGE * 1.5, RANGE, MULTIPLIER)
                        - ManualLinkRule.barProgress(RANGE, RANGE, MULTIPLIER);
        assertions++;
        if (Math.abs(greenStep - redStep) < 1.0E-4F) {
            failures++;
            System.out.println("[pullwires] FAIL 两段斜率相同的话就不是分段线性了");
        }
        assertions++;
        if (greenStep <= redStep) {
            failures++;
            System.out.println("[pullwires] FAIL 绿色段覆盖 3/4 条、红色段只覆盖 1/4 条，绿色段每格应走得更快");
        }

        if (failures == 0) {
            System.out.println("[pullwires] ManualLinkRule ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] ManualLinkRule FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    private static void expect(String name, ManualLinkFeedback expected, double distance) {
        expectWithMultiplier(name, expected, distance, MULTIPLIER);
    }

    private static void expectWithMultiplier(
        String name, ManualLinkFeedback expected, double distance, double multiplier
    ) {
        assertions++;
        ManualLinkFeedback actual = ManualLinkRule.feedback(distance, RANGE, multiplier);
        if (actual == expected) return;
        failures++;
        System.out.println("[pullwires] FAIL " + name + "：期望 " + expected + "，实际 " + actual);
    }

    private static void expectProgress(String name, float expected, double distance) {
        assertions++;
        float actual = ManualLinkRule.barProgress(distance, RANGE, MULTIPLIER);
        if (Math.abs(actual - expected) < 1.0E-4F) return;
        failures++;
        System.out.println("[pullwires] FAIL " + name + "：期望 " + expected + "，实际 " + actual);
    }
}
