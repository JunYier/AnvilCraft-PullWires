package dev.anvilcraft.pullwires.power.link;

import java.util.Collection;
import javax.annotation.Nullable;

/**
 * 输电杆并网判定的纯逻辑部分，不依赖任何游戏类型，便于单独验证。
 *
 * <h2>一套距离口径（V1.12.7 起）</h2>
 * <p>杆间组网与手动连接都读同一个标量 {@link #boxDistance}（三轴分量的最大者）。
 * 它让「自动连接范围」与「手动连接范围」保持同一量纲，于是三档范围天然嵌套：</p>
 * <pre>
 * R_supply ≤ 自动连接范围 R_auto ≤ 手动连接范围 R_manual
 * </pre>
 * <p>历史教训：<b>直线距离</b>版本在斜角方向只有 {@code R}，比本体立方体能到的
 * {@code (R+0.5)·√3} 还短（够得着的角落连不上）；<b>盒子相交</b>版本的有效距离翻成
 * {@code 2R+1}，反超了手动连接范围，导致手动连接找不到任何可连对象。两者都不对。</p>
 *
 * <p>判定候选杆能否并入既有电网时，两种命中一个即可：候选杆的供电框与电网中任意元件的供电框相交
 * （机器走本体的包围盒语义），或者候选杆落在电网中某个中继元件的连接范围内。
 * 若允许连接范围去和机器的供电框比，一根自动连接范围很大的杆就能从很远处给机器供电，
 * 那就违背了「自动连接范围不供电」。</p>
 */
public final class PowerLinkRule {
    private PowerLinkRule() {
    }

    /**
     * 轴对齐包围盒。{@link #intersects} 的边界语义与 {@code net.minecraft.world.phys.AABB} 一致：
     * 仅接触不算相交。
     *
     * <p>只用于<b>供电</b>判定（机器与杆之间），沿用本体语义；杆间组网走
     * {@link #boxDistance} 那条路。</p>
     */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        /** 与 {@code AABB.ofSize(center, size, size, size)} 等价。 */
        public static Box ofCube(double centerX, double centerY, double centerZ, double size) {
            double half = size / 2.0;
            return new Box(
                centerX - half, centerY - half, centerZ - half,
                centerX + half, centerY + half, centerZ + half
            );
        }

        public boolean intersects(Box other) {
            return this.minX < other.maxX && this.maxX > other.minX
                   && this.minY < other.maxY && this.maxY > other.minY
                   && this.minZ < other.maxZ && this.maxZ > other.minZ;
        }
    }

    /** 参与组网的输电杆：中心坐标 + 连接范围。 */
    public record LinkNode(double x, double y, double z, int range) {
    }

    /**
     * 「曼哈顿距离的长边长度」：三轴分量里最大的那个（车比雪夫距离）。
     *
     * <p>它就是包围盒判定的标量形式——{@code max(|dx|,|dy|,|dz|) ≤ R} 等价于
     * 「另一个点落在以我为心、半边长 R 的轴对齐立方体内」，也就是本体 {@code getShape()} 的几何。
     * 用它可以<b>把距离显示、Boss 条进度、盘放置判定全部统一到同一个数上</b>，
     * 不需要处理方向相关的边界。</p>
     */
    public static double boxDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.max(Math.abs(x1 - x2), Math.max(Math.abs(y1 - y2), Math.abs(z1 - z2)));
    }

    /**
     * 两根杆能否自动组网：候选杆的中心落在对方连接范围内。
     *
     * <p>用 {@link #boxDistance} 与<b>较大</b>的那个范围比较。早期版本用直线距离、
     * 后来又用「两个盒子相交」，前者在斜角方向比本体的范围还短，后者的有效距离会翻成
     * {@code 2R+1} 从而反超手动连接范围；现在这个写法既与本体的立方体几何一致，
     * 又让 {@code R_auto} 保持「多少格」的字面含义，三档范围因此天然嵌套。</p>
     */
    public static boolean linked(LinkNode first, LinkNode second) {
        double limit = Math.max(first.range(), second.range());
        return boxDistance(first.x(), first.y(), first.z(), second.x(), second.y(), second.z()) <= limit;
    }

    /** 连接范围 = max(自身供电范围, 配置的自动连接范围)。 */
    public static int linkRange(int componentSupplyRange, int configuredAutoConnectRange) {
        return Math.max(componentSupplyRange, configuredAutoConnectRange);
    }

    /**
     * 判断一根输电杆能否并入既有电网。
     *
     * @param candidateSupply 候选杆的供电框
     * @param gridSupply      电网中所有元件的供电框
     * @param candidate       候选杆的连接节点
     * @param gridTransmitters 电网中所有中继元件的连接节点
     */
    public static boolean canTransmitterJoin(
        Box candidateSupply,
        Collection<Box> gridSupply,
        LinkNode candidate,
        Collection<LinkNode> gridTransmitters
    ) {
        for (Box box : gridSupply) {
            if (candidateSupply.intersects(box)) return true;
        }
        // candidate 为 null 表示「本种类不参与杆间连接判定」（跨维度杆），只走供电框那条路。
        if (candidate == null) return false;
        for (LinkNode other : gridTransmitters) {
            if (linked(candidate, other)) return true;
        }
        return false;
    }
}
