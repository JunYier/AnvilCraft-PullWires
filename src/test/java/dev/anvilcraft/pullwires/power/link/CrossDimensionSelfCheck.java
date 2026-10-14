package dev.anvilcraft.pullwires.power.link;

/**
 * 跨维度连接的纯逻辑自检（M10）。
 *
 * <p>盯三件事：<b>「不受距离限制」只给跨维度杆</b>、<b>跨维度必须两端都是跨维度杆且开关打开</b>、
 * <b>损耗折算永远不会放大传输量</b>。这三条都是「配置写错 / 存档里有旧数据」时最容易出问题的放宽规则，
 * 一旦破防就是凭空发电或凭空连接。</p>
 *
 * <p><b>只碰纯逻辑类</b>（{@link CrossDimensionRule}、{@link InterconnectSettlement}、{@link ManualLinkRule}）。
 * 自检的运行时类路径里没有 Minecraft，任何牵到 {@code PoleKind}、{@code BlockState} 的类
 * 都会在加载阶段就 {@code NoClassDefFoundError}。</p>
 */
public final class CrossDimensionSelfCheck {
    private CrossDimensionSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        int assertions = 0;
        int failures = 0;

        // --- ★ 跨维度准入：必须「两端都是跨维度杆」且开关打开 -------------------
        // (跨维度?, 主端是跨维度杆?, 目标跨维度杆?, 开关, 距离, 主端范围) → 期望
        assertions += expect(
            CrossDimensionRule.verdict(true, true, true, true, 0.0, 0),
            CrossDimensionRule.Verdict.ALLOW, "两根跨维度杆 + 开关打开 → 放行"
        );
        assertions += expect(
            CrossDimensionRule.verdict(true, true, true, false, 0.0, 0),
            CrossDimensionRule.Verdict.CROSS_DIMENSION_DENIED, "开关关闭 → 拒绝"
        );
        // 只要有一端不是跨维度杆，就不能跨维度。
        assertions += expect(
            CrossDimensionRule.verdict(true, true, false, true, 0.0, 0),
            CrossDimensionRule.Verdict.CROSS_DIMENSION_DENIED, "目标不是跨维度杆 → 拒绝"
        );
        assertions += expect(
            CrossDimensionRule.verdict(true, false, true, true, 0.0, 0),
            CrossDimensionRule.Verdict.CROSS_DIMENSION_DENIED, "主端不是跨维度杆 → 拒绝"
        );
        assertions += expect(
            CrossDimensionRule.verdict(true, false, false, true, 0.0, 256),
            CrossDimensionRule.Verdict.CROSS_DIMENSION_DENIED,
            "两根普通杆即使范围再大也不得跨维度"
        );

        // --- ★ 同维度：跨维度杆不受距离限制 -------------------------------------
        assertions += expect(
            CrossDimensionRule.verdict(false, true, false, true, 99999.0, 0),
            CrossDimensionRule.Verdict.ALLOW, "跨维度杆同维度连普通杆：无距离限制"
        );
        assertions += expect(
            CrossDimensionRule.verdict(false, true, true, true, 99999.0, 0),
            CrossDimensionRule.Verdict.ALLOW, "两根跨维度杆同维度相连：无距离限制（AC3.2.3）"
        );
        // 跨维度开关关掉也不影响同维度行走：它管的是「能不能跨维度」，不是「能不能用」。
        assertions += expect(
            CrossDimensionRule.verdict(false, true, true, false, 99999.0, 0),
            CrossDimensionRule.Verdict.ALLOW, "开关关闭不影响同维度连接"
        );

        // --- 同维度：其余杆型受主端范围约束 -------------------------------------
        assertions += expect(
            CrossDimensionRule.verdict(false, false, false, true, 16.0, 16),
            CrossDimensionRule.Verdict.ALLOW, "恰好等于范围 → 放行"
        );
        assertions += expect(
            CrossDimensionRule.verdict(false, false, false, true, 16.1, 16),
            CrossDimensionRule.Verdict.TOO_FAR, "超出范围 → 拒绝"
        );
        assertions += expect(
            CrossDimensionRule.verdict(false, false, true, true, 300.0, 16),
            CrossDimensionRule.Verdict.TOO_FAR, "普通主端连跨维度杆：仍受主端范围约束"
        );

        // --- ★ 损耗折算：只减不增 -------------------------------------------------
        // 注意 checkAmount 必须把失败数**返回**：Java 传值，靠改参数是改不动外面的计数的。
        failures += checkAmount(100, 0.0, 100);
        failures += checkAmount(100, 0.25, 75);
        failures += checkAmount(100, 1.0, 0);
        failures += checkAmount(100, 2.0, 0);      // 越界系数被夹住
        failures += checkAmount(100, -1.0, 100);   // 负数当作无损
        failures += checkAmount(100, Double.NaN, 100);
        failures += checkAmount(0, 0.5, 0);
        failures += checkAmount(-30, 0.5, 0);
        assertions += 8;

        // 送达量恒不超过送出量（这是「不能凭空发电」的形式化表述）。
        boolean neverAmplifies = true;
        for (int amount = 0; amount <= 200; amount++) {
            for (double loss = -0.5; loss <= 1.5; loss += 0.05) {
                int delivered = InterconnectSettlement.deliveredAmount(amount, loss);
                if (delivered < 0 || delivered > amount) neverAmplifies = false;
            }
        }
        assertions++;
        if (!neverAmplifies) {
            failures++;
            System.out.println("[pullwires] FAIL 损耗折算后的送达量必须落在 0..送出量 之间");
        }

        // --- 为什么跨维度杆的 Boss 条要特判 ---------------------------------------
        // 跨维度杆的手动范围是 0，而 barProgress 对 range<=0 一律返回满格（距离本身就是 0 时除外）。
        // 不做特判的话，玩家一绑上跨维度杆就会看到一条按 0 范围算出来的爆红进度条。
        assertions++;
        if (ManualLinkRule.barProgress(50.0, 0, 2.0) != 1.0F) {
            failures++;
            System.out.println("[pullwires] FAIL range=0 时 barProgress 应当恒为满格（Boss 条因此需要跨维度特判）");
        }

        if (failures == 0) {
            System.out.println("[pullwires] CrossDimension ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] CrossDimension FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    /** @return 0 表示通过，1 表示失败 */
    private static int expect(CrossDimensionRule.Verdict actual, CrossDimensionRule.Verdict expected, String what) {
        if (actual == expected) return 0;
        System.out.println("[pullwires] FAIL " + what + "：期望 " + expected + "，实际 " + actual);
        return 1;
    }

    /** @return 0 表示通过，1 表示失败 */
    private static int checkAmount(int amount, double loss, int expected) {
        int actual = InterconnectSettlement.deliveredAmount(amount, loss);
        if (actual == expected) return 0;
        System.out.println("[pullwires] FAIL deliveredAmount(" + amount + ", " + loss + ") 期望 "
                           + expected + "，实际 " + actual);
        return 1;
    }
}
