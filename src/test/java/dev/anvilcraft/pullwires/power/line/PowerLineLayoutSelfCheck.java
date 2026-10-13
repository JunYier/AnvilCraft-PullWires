package dev.anvilcraft.pullwires.power.line;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * {@link PowerLineLayout} 的独立自检程序。
 *
 * <p>刻意不依赖 JUnit：算法本身是纯 Java 的，这里用最普通的断言把需求文档里
 * AC1.1–AC1.5 的形状约束固定下来，通过 Gradle 的 {@code verifyLineLayout} 任务运行。</p>
 */
public final class PowerLineLayoutSelfCheck {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int assertions = 0;

    private PowerLineLayoutSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        assertions = 0;
        FAILURES.clear();

        square();
        hexagon();
        japaneseCharacterRi();
        triangle();
        degenerate();
        disconnectedClusters();

        if (FAILURES.isEmpty()) {
            System.out.println("[pullwires] PowerLineLayout ok (" + assertions + " assertions)");
            return 0;
        }
        FAILURES.forEach(failure -> System.out.println("[pullwires] FAIL PowerLineLayout: " + failure));
        System.out.println("[pullwires] PowerLineLayout FAILED: "
                           + FAILURES.size() + " of " + assertions + " assertions");
        return FAILURES.size();
    }

    /** AC1.1：四根杆组成方形时只画「口」，不能出现对角线。 */
    private static void square() {
        List<PowerLineLayout.Point2> points = List.of(
            new PowerLineLayout.Point2(0, 0),
            new PowerLineLayout.Point2(4, 0),
            new PowerLineLayout.Point2(4, 4),
            new PowerLineLayout.Point2(0, 4)
        );
        List<PowerLineLayout.Edge> edges = PowerLineLayout.layout(points, alwaysConnectable());
        expectCount("square edge count", edges, 4);
        expectTrue("square has no diagonal 0-2", !hasEdge(edges, 0, 2));
        expectTrue("square has no diagonal 1-3", !hasEdge(edges, 1, 3));
        expectPlanar("square", points, edges);
        expectNoOverlap("square", points, edges);
    }

    /** AC1.2：六根杆组成正六边形时只画六边形，不能出现穿心边。 */
    private static void hexagon() {
        double radius = 4;
        List<PowerLineLayout.Point2> points = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i);
            points.add(new PowerLineLayout.Point2(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        List<PowerLineLayout.Edge> edges = PowerLineLayout.layout(points, alwaysConnectable());
        expectCount("hexagon edge count", edges, 6);
        // 穿心边：正六边形中所有非相邻顶点之间的距离都不应被选中（相邻边长为 radius）。
        for (PowerLineLayout.Edge edge : edges) {
            expectTrue(
                "hexagon edge " + edge.a() + "-" + edge.b() + " is a side",
                distance(points.get(edge.a()), points.get(edge.b())) < radius * 1.5
            );
        }
        expectPlanar("hexagon", points, edges);
        expectNoOverlap("hexagon", points, edges);
    }

    /** AC1.3：六根杆组成「日」字时为 7 段，且不存在「整段盖住两个半段」的重复绘制。 */
    private static void japaneseCharacterRi() {
        List<PowerLineLayout.Point2> points = List.of(
            new PowerLineLayout.Point2(0, 2), // 0 左上
            new PowerLineLayout.Point2(2, 2), // 1 右上
            new PowerLineLayout.Point2(0, 1), // 2 左中
            new PowerLineLayout.Point2(2, 1), // 3 右中
            new PowerLineLayout.Point2(0, 0), // 4 左下
            new PowerLineLayout.Point2(2, 0)  // 5 右下
        );
        List<PowerLineLayout.Edge> edges = PowerLineLayout.layout(points, alwaysConnectable());
        expectCount("ri edge count", edges, 7);
        expectTrue("ri keeps the middle bar", hasEdge(edges, 2, 3));
        expectNoOverlap("ri", points, edges);
        expectPlanar("ri", points, edges);
    }

    private static void triangle() {
        List<PowerLineLayout.Point2> points = List.of(
            new PowerLineLayout.Point2(0, 0),
            new PowerLineLayout.Point2(3, 0),
            new PowerLineLayout.Point2(0, 3)
        );
        List<PowerLineLayout.Edge> edges = PowerLineLayout.layout(points, alwaysConnectable());
        expectCount("triangle edge count", edges, 3);
    }

    /** 退化输入：0/1 根杆无边；2 根杆一条边；超出连接范围则一条都不画。 */
    private static void degenerate() {
        expectCount("empty", PowerLineLayout.layout(List.of(), alwaysConnectable()), 0);
        expectCount(
            "single point",
            PowerLineLayout.layout(List.of(new PowerLineLayout.Point2(0, 0)), alwaysConnectable()),
            0
        );

        List<PowerLineLayout.Point2> pair = List.of(
            new PowerLineLayout.Point2(0, 0),
            new PowerLineLayout.Point2(3, 0)
        );
        expectCount("pair in range", PowerLineLayout.layout(pair, alwaysConnectable()), 1);
        expectCount("pair out of range", PowerLineLayout.layout(pair, (a, b) -> false), 0);

        List<PowerLineLayout.Point2> collinear = List.of(
            new PowerLineLayout.Point2(0, 0),
            new PowerLineLayout.Point2(1, 0),
            new PowerLineLayout.Point2(2, 0)
        );
        List<PowerLineLayout.Edge> collinearEdges = PowerLineLayout.layout(collinear, alwaysConnectable());
        expectCount("collinear count", collinearEdges, 2);
        expectTrue("collinear connects 0-1", hasEdge(collinearEdges, 0, 1));
        expectTrue("collinear connects 1-2", hasEdge(collinearEdges, 1, 2));

        List<PowerLineLayout.Point2> duplicated = List.of(
            new PowerLineLayout.Point2(1, 1),
            new PowerLineLayout.Point2(1, 1),
            new PowerLineLayout.Point2(5, 1)
        );
        expectCount("duplicate projection", PowerLineLayout.layout(duplicated, alwaysConnectable()), 1);
    }

    /** 两簇相距很远时各自成林，拉不出跨簇的线。 */
    private static void disconnectedClusters() {
        List<PowerLineLayout.Point2> points = List.of(
            new PowerLineLayout.Point2(0, 0),
            new PowerLineLayout.Point2(1, 0),
            new PowerLineLayout.Point2(100, 0),
            new PowerLineLayout.Point2(101, 0)
        );
        List<PowerLineLayout.Edge> edges = PowerLineLayout.layout(points, (a, b) -> {
            double dx = points.get(a).x() - points.get(b).x();
            double dz = points.get(a).z() - points.get(b).z();
            return dx * dx + dz * dz < 10 * 10;
        });
        expectCount("two clusters edge count", edges, 2);
        expectTrue("cluster A connected", hasEdge(edges, 0, 1));
        expectTrue("cluster B connected", hasEdge(edges, 2, 3));
    }

    private static BiPredicate<Integer, Integer> alwaysConnectable() {
        return (a, b) -> true;
    }

    private static boolean hasEdge(List<PowerLineLayout.Edge> edges, int a, int b) {
        return edges.contains(new PowerLineLayout.Edge(a, b));
    }

    private static double distance(PowerLineLayout.Point2 a, PowerLineLayout.Point2 b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void expectCount(String name, List<PowerLineLayout.Edge> edges, int expected) {
        assertions++;
        if (edges.size() != expected) {
            FAILURES.add(name + ": expected " + expected + " edges but got " + edges.size());
        }
    }

    private static void expectTrue(String name, boolean condition) {
        assertions++;
        if (!condition) FAILURES.add(name);
    }

    /** 任意两条不共端点的边都不得真正相交。 */
    private static void expectPlanar(String name, List<PowerLineLayout.Point2> points, List<PowerLineLayout.Edge> edges) {
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                PowerLineLayout.Edge first = edges.get(i);
                PowerLineLayout.Edge second = edges.get(j);
                if (sharesEndpoint(first, second)) continue;
                assertions++;
                if (properlyCross(
                    points.get(first.a()), points.get(first.b()),
                    points.get(second.a()), points.get(second.b())
                )) {
                    FAILURES.add(name + ": edges " + first + " and " + second + " cross");
                }
            }
        }
    }

    /** 任意两条边都不得共线重叠（防止「整段盖住两个半段」）。 */
    private static void expectNoOverlap(String name, List<PowerLineLayout.Point2> points, List<PowerLineLayout.Edge> edges) {
        for (int i = 0; i < edges.size(); i++) {
            for (int j = i + 1; j < edges.size(); j++) {
                PowerLineLayout.Edge first = edges.get(i);
                PowerLineLayout.Edge second = edges.get(j);
                if (sharesEndpoint(first, second)) continue;
                assertions++;
                if (collinearOverlap(
                    points.get(first.a()), points.get(first.b()),
                    points.get(second.a()), points.get(second.b())
                )) {
                    FAILURES.add(name + ": edges " + first + " and " + second + " overlap");
                }
            }
        }
    }

    private static boolean sharesEndpoint(PowerLineLayout.Edge a, PowerLineLayout.Edge b) {
        return a.a() == b.a() || a.a() == b.b() || a.b() == b.a() || a.b() == b.b();
    }

    private static double orient(
        PowerLineLayout.Point2 p, PowerLineLayout.Point2 q, PowerLineLayout.Point2 r
    ) {
        return (q.x() - p.x()) * (r.z() - p.z()) - (q.z() - p.z()) * (r.x() - p.x());
    }

    private static boolean properlyCross(
        PowerLineLayout.Point2 a, PowerLineLayout.Point2 b,
        PowerLineLayout.Point2 c, PowerLineLayout.Point2 d
    ) {
        double first = orient(a, b, c);
        double second = orient(a, b, d);
        double third = orient(c, d, a);
        double fourth = orient(c, d, b);
        return first * second < 0 && third * fourth < 0;
    }

    private static boolean collinearOverlap(
        PowerLineLayout.Point2 a, PowerLineLayout.Point2 b,
        PowerLineLayout.Point2 c, PowerLineLayout.Point2 d
    ) {
        if (orient(a, b, c) != 0 || orient(a, b, d) != 0) return false;
        double axisX = b.x() - a.x();
        double axisZ = b.z() - a.z();
        double lengthSquared = axisX * axisX + axisZ * axisZ;
        if (lengthSquared == 0) return false;
        double first = ((c.x() - a.x()) * axisX + (c.z() - a.z()) * axisZ) / lengthSquared;
        double second = ((d.x() - a.x()) * axisX + (d.z() - a.z()) * axisZ) / lengthSquared;
        double low = Math.min(first, second);
        double high = Math.max(first, second);
        return high > 0 && low < 1;
    }
}
