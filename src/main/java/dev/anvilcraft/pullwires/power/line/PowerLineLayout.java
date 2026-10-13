package dev.anvilcraft.pullwires.power.line;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;

/**
 * 电网连线的「最简闭合图形」求解器。
 *
 * <p>给定一组输电杆的平面投影点，输出应当连线的杆对下标。结果定义为
 * <b>凸包 ∪ 欧氏最小生成树</b>：它同时满足连通、平面（无交叉）、总长最小、包住所有有效节点，
 * 因此四根杆画成「口」而不是带对角线的「X」，六根杆画成六边形而不是三角网。</p>
 *
 * <p>本类不依赖任何 Minecraft 类型，可脱离游戏单独验证。</p>
 */
public final class PowerLineLayout {
    private PowerLineLayout() {
    }

    /**
     * 投影到 XZ 平面的点，仅用于决定连线拓扑。
     *
     * <p>调用方使用的投影带有 {@code (y - 1024) / 2048} 的微小偏移，
     * 用来把垂直堆叠的杆在平面上分开；实际连线仍应使用完整的三维坐标。</p>
     */
    public record Point2(double x, double z) {
    }

    /**
     * 一条无向边。构造时保证 {@code a < b}，因此同一条边只有一种表示，可直接去重。
     */
    public record Edge(int a, int b) implements Comparable<Edge> {
        public Edge {
            if (a > b) {
                int swap = a;
                a = b;
                b = swap;
            }
        }

        @Override
        public int compareTo(Edge other) {
            int byA = Integer.compare(this.a, other.a);
            return byA != 0 ? byA : Integer.compare(this.b, other.b);
        }
    }

    /**
     * 求解最简闭合图形。
     *
     * @param points      各杆的平面投影点，下标即杆的下标
     * @param connectable 判断两个下标之间是否允许连线（例如是否在自动连接范围内）；
     *                    只有返回 true 的点对才可能出现在结果中
     * @return 去重且有序的边列表
     */
    public static List<Edge> layout(List<Point2> points, BiPredicate<Integer, Integer> connectable) {
        if (points.size() < 2) return List.of();

        // 投影完全重合的杆在拓扑上是同一个节点，先归并，避免出现零长边或重复绘制。
        Collapsed collapsed = Collapsed.of(points);
        List<Point2> nodes = collapsed.points();
        BiPredicate<Integer, Integer> canConnect =
            (a, b) -> connectable.test(collapsed.original(a), collapsed.original(b));
        if (nodes.size() < 2) return List.of();

        Set<Edge> hull = new HashSet<>();
        for (Edge edge : hullEdges(nodes)) {
            if (canConnect.test(edge.a(), edge.b())) hull.add(edge);
        }

        TreeSet<Edge> result = new TreeSet<>(hull);
        result.addAll(minimumSpanningTree(nodes, canConnect, hull));
        return List.copyOf(result);
    }

    /**
     * 凸包边界，按 Andrew 单调链求解。
     *
     * <p>与常见实现不同，这里<b>保留边界上的共线点</b>（出栈条件用 {@code cross < 0} 而非 {@code <= 0}）。
     * 这样「日」字形左右两条竖边的中点会留在凸包上，凸包直接给出被中点切开的 6 条短边，
     * 不会出现「整段盖住两个半段」的重复绘制。</p>
     */
    private static List<Edge> hullEdges(List<Point2> points) {
        List<Integer> ordered = sortedIndices(points);
        int size = ordered.size();
        if (size < 2) return List.of();
        if (size == 2) return List.of(new Edge(ordered.get(0), ordered.get(1)));

        int[] stack = new int[2 * size];
        int top = 0;
        for (int i = 0; i < size; i++) {
            int current = ordered.get(i);
            while (top >= 2 && cross(points.get(stack[top - 2]), points.get(stack[top - 1]), points.get(current)) < 0) {
                top--;
            }
            stack[top++] = current;
        }
        int lowerBound = top + 1;
        for (int i = size - 2; i >= 0; i--) {
            int current = ordered.get(i);
            while (top >= lowerBound
                   && cross(points.get(stack[top - 2]), points.get(stack[top - 1]), points.get(current)) < 0) {
                top--;
            }
            stack[top++] = current;
        }

        List<Edge> edges = new ArrayList<>(top - 1);
        for (int i = 0; i + 1 < top; i++) {
            edges.add(new Edge(stack[i], stack[i + 1]));
        }
        return edges;
    }

    /**
     * 最小生成树（Kruskal）。
     *
     * <p><b>等长边优先取非凸包边。</b>凸包边无论如何都会画出来，用它们去连接两个连通分量等于白白浪费一次连接机会；
     * 让生成树优先走内部边，才能把「日」字的中横补上（上边框、下边框、中横长度相同，
     * 若按下标顺序取就会选到上边框，结果退化成矩形）。总长度不变，只是等长解里选了信息量更大的那个。</p>
     *
     * <p>候选边取全对枚举：一座电网里的输电杆通常不到 100 根（≤ 5000 对），
     * 全对枚举既简单又能得到<b>精确</b>最小生成树。若将来数量级显著变大，
     * 可改用「欧氏最小生成树 ⊆ Delaunay 三角剖分」这一性质，
     * 复用 {@code DelaunayTriangulator} 的边集再跑 Kruskal，保持精确且降为 {@code O(n log n)}。</p>
     */
    private static List<Edge> minimumSpanningTree(
        List<Point2> points, BiPredicate<Integer, Integer> connectable, Set<Edge> hullEdges
    ) {
        int count = points.size();
        List<Edge> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (!connectable.test(i, j)) continue;
                candidates.add(new Edge(i, j));
            }
        }
        candidates.sort(Comparator
            .comparingDouble((Edge edge) -> distanceSquared(points.get(edge.a()), points.get(edge.b())))
            .thenComparingInt(edge -> hullEdges.contains(edge) ? 1 : 0)
            .thenComparing(Comparator.naturalOrder()));

        int[] parent = new int[count];
        for (int i = 0; i < count; i++) parent[i] = i;

        List<Edge> chosen = new ArrayList<>();
        for (Edge edge : candidates) {
            int rootA = find(parent, edge.a());
            int rootB = find(parent, edge.b());
            if (rootA == rootB) continue;
            parent[rootA] = rootB;
            chosen.add(edge);
        }
        return chosen;
    }

    private static List<Integer> sortedIndices(List<Point2> points) {
        List<Integer> indices = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) indices.add(i);
        indices.sort(Comparator
            .comparingDouble((Integer index) -> points.get(index).x())
            .thenComparingDouble(index -> points.get(index).z())
            .thenComparingInt(Integer::intValue));
        return indices;
    }

    private static int find(int[] parent, int node) {
        int root = node;
        while (parent[root] != root) root = parent[root];
        while (parent[node] != root) {
            int next = parent[node];
            parent[node] = root;
            node = next;
        }
        return root;
    }

    private static double cross(Point2 origin, Point2 a, Point2 b) {
        return (a.x() - origin.x()) * (b.z() - origin.z()) - (a.z() - origin.z()) * (b.x() - origin.x());
    }

    private static double distanceSquared(Point2 a, Point2 b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }

    private static boolean sameCoordinate(Point2 a, Point2 b) {
        return Double.compare(a.x(), b.x()) == 0 && Double.compare(a.z(), b.z()) == 0;
    }

    /**
     * 投影重合点的归并结果。
     *
     * @param points   归并后的点，顺序取每组中的首个原下标
     * @param original 归并后下标 → 代表原下标
     * @param reduced  原下标 → 归并后下标
     */
    private record Collapsed(List<Point2> points, int[] original, int[] reduced) {
        static Collapsed of(List<Point2> source) {
            int count = source.size();
            int[] original = new int[count];
            int[] reduced = new int[count];
            List<Point2> points = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Point2 candidate = source.get(i);
                int found = -1;
                for (int k = 0; k < points.size(); k++) {
                    if (sameCoordinate(points.get(k), candidate)) {
                        found = k;
                        break;
                    }
                }
                if (found < 0) {
                    found = points.size();
                    points.add(candidate);
                    original[found] = i;
                }
                reduced[i] = found;
            }
            return new Collapsed(List.copyOf(points), original, reduced);
        }

        int original(int reducedIndex) {
            return this.original[reducedIndex];
        }
    }
}
