package dev.anvilcraft.pullwires.power.link;

/**
 * {@link PoleRangeRule} 的纯逻辑自检：倍数 → 格数的换算，以及「三档必须嵌套」这个不变式。
 *
 * <p>这条不变式现在承担了一个额外职责：配置里存的是<b>倍数</b>，而本体 R 是玩家可改的。
 * 只要换算本身保证 {@code R_supply ≤ R_auto ≤ R_manual}，玩家怎么调 R 都不会让旧存档里
 * 已经放好的杆越界。</p>
 */
public final class PoleRangeRuleSelfCheck {
    private PoleRangeRuleSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        Checker check = new Checker();

        // --- 默认值必须换算出预期的方块数 ---------------------------------------
        // 本体 R 默认：输电杆 8、远程桩 16。
        check.eq(12, PoleRangeRule.scale(8, 1.5), "输电杆 autoR = 1.5 x 8");
        check.eq(16, PoleRangeRule.scale(8, 2.0), "输电杆 manualR = 2 x 8");
        check.eq(24, PoleRangeRule.scale(16, 1.5), "远程桩 autoR = 1.5 x 16");
        check.eq(32, PoleRangeRule.scale(16, 2.0), "远程桩 manualR = 2 x 16");
        check.eq(256, PoleRangeRule.scale(8, 32.0), "超远程杆 autoR = 32 x 8");
        check.eq(512, PoleRangeRule.scale(8, 64.0), "超远程杆 manualR = 64 x 8");

        // --- 0 表示「该档禁用」，不能被四舍五入成 1 ------------------------------
        check.eq(0, PoleRangeRule.scale(8, 0.0), "倍数 0 = 禁用");
        check.eq(0, PoleRangeRule.scale(0, 1.5), "基准 0 = 禁用");
        check.eq(1, PoleRangeRule.scale(8, 0.01), "极小的非零倍数应得到至少 1 格");
        check.eq(0, PoleRangeRule.scale(8, -1.0), "负倍数按禁用处理");
        check.eq(0, PoleRangeRule.scale(8, Double.NaN), "NaN 按禁用处理");
        check.eq(0, PoleRangeRule.scale(-4, 1.5), "负基准按禁用处理");

        // --- ★ 不变式：R_auto ≤ R_manual，且三档非负 ----------------------------
        boolean nested = true;
        for (int base : new int[] {1, 2, 8, 16, 64}) {
            for (double auto = 0.0; auto <= 3.0; auto += 0.1) {
                for (double manual = 0.0; manual <= 3.0; manual += 0.1) {
                    PoleRangeRule.Ranges r = PoleRangeRule.resolve(base, base, auto, manual);
                    if (r.supplyRange() < 0
                        || r.autoConnectRange() < 0
                        || r.manualConnectRange() < 0
                        || r.autoConnectRange() > r.manualConnectRange()) {
                        nested = false;
                    }
                }
            }
        }
        check.that(nested, "R_auto ≤ R_manual 必须对任意倍数组合成立");

        // --- 玩家把 manual 倍数调得比 auto 小：收敛到 auto，而不是变成更小的值 -----
        PoleRangeRule.Ranges inverted = PoleRangeRule.resolve(2, 8, 2.0, 1.0);
        check.eq(16, inverted.autoConnectRange(), "auto = 2 x 8");
        check.eq(16, inverted.manualConnectRange(), "manual 倍数更小时抬到 auto");
        check.eq(2, inverted.supplyRange(), "供电范围原样透传");

        // --- 默认配置：三档确实嵌套 --------------------------------------------
        for (int base : new int[] {8, 16}) {
            PoleRangeRule.Ranges r = PoleRangeRule.resolve(base, base, 1.5, 2.0);
            check.that(
                r.supplyRange() <= r.autoConnectRange() && r.autoConnectRange() <= r.manualConnectRange(),
                "本体杆 base=" + base + " 的默认倍数应得到嵌套的三档 → " + r
            );
        }

        // --- 超远程杆默认值（R = 8 时）-----------------------------------------
        PoleRangeRule.Ranges ultra = PoleRangeRule.resolve(2, 8, 32.0, 64.0);
        check.eq(2, ultra.supplyRange(), "超远程杆供电范围 = 2");
        check.eq(256, ultra.autoConnectRange(), "超远程杆 autoR = 256");
        check.eq(512, ultra.manualConnectRange(), "超远程杆 manualR = 512");
        // M9 的 AC3.1.1 要求相距 200 格自动组网，autoR 必须撑得住。
        check.that(ultra.autoConnectRange() >= 200, "超远程杆 autoR 必须 ≥ 200（AC3.1.1）");

        // --- 跨维度杆：两档都是 0，即不组网、也只能靠绑定连接 --------------------
        PoleRangeRule.Ranges cross = PoleRangeRule.resolve(0, 8, 0.0, 0.0);
        check.eq(0, cross.supplyRange(), "跨维度杆不供电");
        check.eq(0, cross.autoConnectRange(), "跨维度杆不自动组网");
        check.eq(0, cross.manualConnectRange(), "跨维度杆不走距离判定");

        return check.report("PoleRange");
    }

    /** 断言计数器：省得每次手写 {@code assertions++}（漏写会让断言数少报）。 */
    private static final class Checker {
        int assertions;
        int failures;

        void eq(int expected, int actual, String what) {
            assertions++;
            if (expected == actual) return;
            failures++;
            System.out.println("[pullwires] FAIL " + what + "：期望 " + expected + "，实际 " + actual);
        }

        void that(boolean ok, String what) {
            assertions++;
            if (ok) return;
            failures++;
            System.out.println("[pullwires] FAIL " + what);
        }

        int report(String name) {
            if (failures == 0) {
                System.out.println("[pullwires] " + name + " ok (" + assertions + " assertions)");
            } else {
                System.out.println("[pullwires] " + name + " FAILED: " + failures + "/" + assertions);
            }
            return failures;
        }
    }
}
