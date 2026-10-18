package dev.anvilcraft.pullwires.power.link;

import java.util.List;

/**
 * {@link PowerLinkRule} 的独立自检程序，不依赖游戏运行环境。
 *
 * <p>固定使用需求里的比例：本体距离 R = 8，autoR = 1.5R = 12，manualR = 2R = 16。
 * 连接范围取 {@code max(自身供电范围, autoR)}。</p>
 */
public final class PowerLinkRuleSelfCheck {
    private static final int SUPPLY_RANGE = 8;
    private static final int AUTO_RANGE = 12;

    private static int assertions;
    private static int failures;

    private PowerLinkRuleSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        assertions = 0;
        failures = 0;

        // 连接范围 = max(供电范围, autoR)
        expectTrue("普通杆连接范围 = autoR", PowerLinkRule.linkRange(SUPPLY_RANGE, AUTO_RANGE) == AUTO_RANGE);
        expectTrue("远程桩不被压短", PowerLinkRule.linkRange(16, AUTO_RANGE) == 16);
        expectTrue("供电范围为 0 时取 autoR", PowerLinkRule.linkRange(0, AUTO_RANGE) == AUTO_RANGE);

        // ---- 杆间组网：三轴分量的最大者（「曼哈顿距离的长边长度」）与范围比较 ----
        expectTrue("贴在一起应相连", PowerLinkRule.linked(node(0, 0, 0, 12), node(1, 0, 0, 12)));
        expectTrue("恰好等于范围应相连", PowerLinkRule.linked(node(0, 0, 0, 12), node(12, 0, 0, 12)));
        expectTrue("刚超过范围不应相连", !PowerLinkRule.linked(node(0, 0, 0, 12), node(12.01, 0, 0, 12)));
        expectTrue("范围之外不相连", !PowerLinkRule.linked(node(0, 0, 0, 12), node(20, 0, 0, 12)));

        // 关键回归一（用户报过）：斜角方向必须与本体立方体几何一致。
        // 直线距离版本在 (12,0,12) 处算出 17 格而判定相连失败；本体盒子在斜角能到 (R+0.5)·√3。
        expectTrue("斜角 (12,0,12) 应相连（直线距离版本会在这里失败）",
            PowerLinkRule.linked(node(0, 0, 0, 12), node(12, 0, 12, 12)));
        expectTrue("斜角超出范围仍不相连", !PowerLinkRule.linked(node(0, 0, 0, 12), node(13, 0, 13, 12)));
        expectTrue("垂直方向同样生效", PowerLinkRule.linked(node(0, 0, 0, 12), node(0, 12, 0, 12)));
        expectTrue("垂直方向超出即断开", !PowerLinkRule.linked(node(0, 0, 0, 12), node(0, 12.01, 0, 12)));

        // 关键回归二：有效距离必须是 R 而不是 2R+1，否则「自动组网范围」会反超「手动连接范围」，
        // 手动连接就再也找不到任何可连对象了。
        expectTrue("25 格（旧盒子相交版本会相连）必须不相连",
            !PowerLinkRule.linked(node(0, 0, 0, 12), node(25, 0, 0, 12)));

        // 两端取较大者，保证「大范围的一方能够到」
        expectTrue("取两端较大范围", PowerLinkRule.linked(node(0, 0, 0, SUPPLY_RANGE), node(11, 0, 0, AUTO_RANGE)));
        expectTrue("两端都小则够不着", !PowerLinkRule.linked(node(0, 0, 0, 2), node(10, 0, 0, 2)));

        // 长边长度本身
        expectTrue("长边长度取三轴最大分量", PowerLinkRule.boxDistance(0, 0, 0, 3, -5, 4) == 5.0);
        expectTrue("长边长度恒非负", PowerLinkRule.boxDistance(3, 3, 3, 3, 3, 3) == 0.0);

        // 并网判定一：候选杆的供电框与电网中机器的供电框相交（贴着机器放杆）
        expectTrue("贴近机器应能并网", PowerLinkRule.canTransmitterJoin(
            box(0, SUPPLY_RANGE), List.of(box(3, SUPPLY_RANGE)), node(0, 0, 0, AUTO_RANGE), List.of()
        ));

        // 并网判定二：与电网中已有的杆在连接范围内
        expectTrue("在连接范围内应能并网", PowerLinkRule.canTransmitterJoin(
            box(0, SUPPLY_RANGE), List.of(box(100, SUPPLY_RANGE)),
            node(0, 0, 0, AUTO_RANGE), List.of(node(12, 0, 0, AUTO_RANGE))
        ));

        // 并网判定三：电网里只有一台远处的机器 —— 自动连接范围不该把杆拉过去（否则等于远距离供电）
        expectTrue("不应靠连接范围够到远处的机器", !PowerLinkRule.canTransmitterJoin(
            box(0, SUPPLY_RANGE), List.of(box(100, SUPPLY_RANGE)),
            node(0, 0, 0, AUTO_RANGE), List.of()
        ));

        if (failures == 0) {
            System.out.println("[pullwires] PowerLinkRule ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] PowerLinkRule FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    private static PowerLinkRule.LinkNode node(double x, double y, double z, int range) {
        return new PowerLinkRule.LinkNode(x, y, z, range);
    }

    private static PowerLinkRule.Box box(double offset, int range) {
        double size = range * 2 + 1;
        return PowerLinkRule.Box.ofCube(offset, 0, 0, size);
    }

    private static void expectTrue(String name, boolean condition) {
        assertions++;
        if (condition) return;
        failures++;
        System.out.println("[pullwires] FAIL " + name);
    }
}
