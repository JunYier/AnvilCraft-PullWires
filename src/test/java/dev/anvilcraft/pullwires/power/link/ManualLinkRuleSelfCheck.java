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
}
