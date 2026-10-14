package dev.anvilcraft.pullwires.power.link;

import java.util.Collection;
import javax.annotation.Nullable;

/**
 * 输电杆并网判定的纯逻辑部分，不依赖任何游戏类型，便于单独验证。
 *
 * <h2>两套距离口径</h2>
 * <ul>
 *   <li><b>供电</b>（发电机／用电器／储电元件）：沿用本体的<b>包围盒相交</b>，
 *       即 {@code getShape()} 与电网形状相交。语义与既有存档完全一致。</li>
 *   <li><b>杆与杆之间的组网</b>：用<b>直线距离</b>。必须用直线距离，否则三档范围不成立——
 *       包围盒半宽是 {@code range + 0.5}，两根杆各自张开后有效连接距离是 {@code 2 × range + 1}，
 *       会比手动连接范围还大。</li>
 * </ul>
 *
 * <p>连接范围取 {@code max(自身供电范围, 配置的自动连接范围)}，因此
 * 「自动连接范围 ≥ 供电范围」天然成立，供电范围本来就更大的远程输电桩也不会被压短。</p>
 *
 * <p>判定候选杆能否并入既有电网时，两种相交命中一个即可：候选杆的供电框与电网中任意元件的供电框相交，
 * 或者候选杆与电网中某个中继元件的直线距离在连接范围内。若允许连接范围去和机器的供电框比，
 * 一根自动连接范围很大的杆就能从很远处给机器供电，那就违背了「自动连接范围不供电」。</p>
 */
public final class PowerLinkRule {
    private PowerLinkRule() {
    }

    /**
     * 轴对齐包围盒。{@link #intersects} 的边界语义与 {@code net.minecraft.world.phys.AABB} 一致：
     * 仅接触不算相交。
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

    /** 两根杆是否在连接范围内（直线距离，闭区间）。 */
    public static boolean linked(LinkNode first, LinkNode second) {
        double limit = Math.max(first.range(), second.range());
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz <= limit * limit;
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
