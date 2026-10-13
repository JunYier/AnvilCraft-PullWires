package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.power.line.PowerLineLayout;

import java.util.List;

/**
 * {@link PowerLinkRule} 的独立自检程序，不依赖游戏运行环境。
 *
 * <p>固定使用文档中的默认值：供电范围 8（框边长 17）、自动连接范围 16（框边长 33）。</p>
 */
public final class PowerLinkRuleSelfCheck {
    private static final int SUPPLY_RANGE = 8;
    private static final int LINK_RANGE = 16;
    private static final double SUPPLY_SIZE = SUPPLY_RANGE * 2 + 1;
    private static final double LINK_SIZE = LINK_RANGE * 2 + 1;

    private PowerLinkRuleSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        int assertions = 0;
        int failures = 0;

        // 相距 25 的两根杆：供电框不重叠（17 < 25），连接框重叠（33 > 25）→ 应当靠自动连接范围组网。
        assertions++;
        if (!PowerLinkRule.canTransmitterJoin(
            supply(0), link(0), List.of(supply(25)), List.of(link(25))
        )) {
            failures++;
            System.out.println("[pullwires] FAIL 杆与杆应在自动连接范围内组网");
        }

        // 相距 25 的杆与用电器：电网里只有用电器，没有中继元件 → 连不上。
        // 这正是「自动连接范围内可以组网，但没有供电功能」。
        assertions++;
        if (PowerLinkRule.canTransmitterJoin(
            supply(0), link(0), List.of(supply(25, 0)), List.of()
        )) {
            failures++;
            System.out.println("[pullwires] FAIL 用电器不应被自动连接范围拉进电网");
        }

        // 杆紧贴用电器（相距 3）：供电框相交 → 应当并入。
        assertions++;
        if (!PowerLinkRule.canTransmitterJoin(
            supply(0), link(0), List.of(supply(3, 0)), List.of()
        )) {
            failures++;
            System.out.println("[pullwires] FAIL 杆应能贴合用电器并网");
        }

        // 相距 100 的两根杆：供电框与连接框都不重叠 → 不能并网。
        assertions++;
        if (PowerLinkRule.canTransmitterJoin(
            supply(0), link(0), List.of(supply(100)), List.of(link(100))
        )) {
            failures++;
            System.out.println("[pullwires] FAIL 超出自动连接范围的杆不应并网");
        }

        // 边界：恰好相切不算相交（与 AABB 语义一致）。
        assertions++;
        if (PowerLinkRule.Box.ofCube(0, 0, 0, SUPPLY_SIZE)
            .intersects(PowerLinkRule.Box.ofCube(SUPPLY_SIZE, 0, 0, SUPPLY_SIZE))) {
            failures++;
            System.out.println("[pullwires] FAIL 恰好相切的包围盒不应判定为相交");
        }

        // 连接框边长必须大于供电框，否则「自动连接范围更大」不成立。
        assertions++;
        if (LINK_SIZE <= SUPPLY_SIZE) {
            failures++;
            System.out.println("[pullwires] FAIL 连接框应大于供电框");
        }

        // 高维方向也要生效（Y 轴）：垂直方向上距离超出即不并网。
        assertions++;
        if (PowerLinkRule.canTransmitterJoin(
            supply(0), link(0), List.of(supply(0, 100)), List.of(link(0, 100))
        )) {
            failures++;
            System.out.println("[pullwires] FAIL 垂直方向超出范围不应并网");
        }

        // 「自动连接范围 ≥ 供电范围」必须恒成立：配置比供电范围小时取供电范围。
        assertions++;
        if (PowerLinkRule.linkRange(8, 16) != 16) {
            failures++;
            System.out.println("[pullwires] FAIL 普通杆的连接范围应取配置值");
        }
        assertions++;
        if (PowerLinkRule.linkRange(16, 8) != 16) {
            failures++;
            System.out.println("[pullwires] FAIL 供电范围更大的远程桩不应被较小的配置压缩");
        }
        assertions++;
        if (PowerLinkRule.linkRange(64, 8) != 64) {
            failures++;
            System.out.println("[pullwires] FAIL 连接范围不得小于供电范围");
        }
        assertions++;
        if (PowerLinkRule.linkRange(0, 16) != 16) {
            failures++;
            System.out.println("[pullwires] FAIL 供电范围为 0 时应取配置值");
        }

        // 回归：视觉连线必须按「连接范围」过滤，不能按「供电范围」。
        // 相距 32 的两根杆在连接范围 16 下相连（33 > 32），在供电范围 8 下不相连（17 < 32）。
        assertions++;
        if (!PowerLinkRule.visualLinkOverlap(0, 0, 0, 16, 32, 0, 0, 16)) {
            failures++;
            System.out.println("[pullwires] FAIL 连接范围下相距 32 的杆应当有连线");
        }
        assertions++;
        if (PowerLinkRule.visualLinkOverlap(0, 0, 0, 8, 32, 0, 0, 8)) {
            failures++;
            System.out.println("[pullwires] FAIL 供电范围下相距 32 的杆不应相连（这条正是曾经用错范围的原因）");
        }
        // 边界：恰好 33 时半宽相切，不算相交。
        assertions++;
        if (PowerLinkRule.visualLinkOverlap(0, 0, 0, 16, 33, 0, 0, 16)) {
            failures++;
            System.out.println("[pullwires] FAIL 恰好相切不应判定为相交");
        }

        // 端到端复现报告的场景：A—B 相距 32，C 在 A 侧面 16 格处。
        // 加入 C 之后 A—B 的连线必须仍然存在（修复前会因为误用供电范围而消失）。
        assertions++;
        if (!layoutKeepsFarPair()) {
            failures++;
            System.out.println("[pullwires] FAIL 加入第三根杆后，A—B 的连线不应消失");
        }

        if (failures == 0) {
            System.out.println("[pullwires] PowerLinkRule ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] PowerLinkRule FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    /** 端到端复现报告的场景：A(0,0) 与 B(32,0) 相距 32，C(0,16) 贴在 A 侧面。 */
    private static boolean layoutKeepsFarPair() {
        List<PowerLineLayout.Point2> points = List.of(
            new PowerLineLayout.Point2(0, 0),
            new PowerLineLayout.Point2(32, 0),
            new PowerLineLayout.Point2(0, 16)
        );
        List<PowerLineLayout.Edge> edges = PowerLineLayout.layout(points, (first, second) ->
            PowerLinkRule.visualLinkOverlap(
                points.get(first).x(), 0, points.get(first).z(), LINK_RANGE,
                points.get(second).x(), 0, points.get(second).z(), LINK_RANGE
            ));
        return edges.contains(new PowerLineLayout.Edge(0, 1));
    }

    private static PowerLinkRule.Box supply(double offset) {
        return PowerLinkRule.Box.ofCube(offset, 0, 0, SUPPLY_SIZE);
    }

    private static PowerLinkRule.Box link(double offset) {
        return PowerLinkRule.Box.ofCube(offset, 0, 0, LINK_SIZE);
    }

    private static PowerLinkRule.Box supply(double offsetX, double offsetY) {
        return PowerLinkRule.Box.ofCube(offsetX, offsetY, 0, SUPPLY_SIZE);
    }

    private static PowerLinkRule.Box link(double offsetX, double offsetY) {
        return PowerLinkRule.Box.ofCube(offsetX, offsetY, 0, LINK_SIZE);
    }
}
