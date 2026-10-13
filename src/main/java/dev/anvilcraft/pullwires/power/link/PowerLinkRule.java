package dev.anvilcraft.pullwires.power.link;

import java.util.Collection;

/**
 * 输电杆并网判定的纯逻辑部分，不依赖任何游戏类型，便于单独验证。
 *
 * <p>两个框的语义：</p>
 * <ul>
 *   <li><b>供电框 supply</b>：元件自身的 {@code getShape()}，即本体已有的 {@code range}。
 *       发电机、用电器、储电元件只能靠供电框相交并入电网。</li>
 *   <li><b>连接框 link</b>：取 {@code max(自身 range, 配置的自动连接范围)}。
 *       只有输电杆之间才用连接框，因此它能扩大「组网」范围却<b>不产生任何供电能力</b>。</li>
 * </ul>
 *
 * <p>判定输电杆能否并入既有电网时，两种相交只要命中一个即可，但两侧口径不同：
 * 候选杆的供电框只能与电网中任意元件的供电框比，候选杆的连接框只能与电网中<b>中继元件</b>的连接框比。
 * 若允许连接框去和机器的供电框比，一根自动连接范围很大的杆就能从很远处给机器供电，
 * 那就违背了「自动连接范围不供电」。</p>
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

    /**
     * 输电杆的连接范围。
     *
     * <p>取 {@code max(自身供电范围, 配置的自动连接范围)}，因此
     * 「自动连接范围 ≥ 供电范围」这条约束天然成立，不需要额外的越界告警；
     * 也不会让供电范围本来就更大的「远程输电桩」因为新配置较小反而连不上近处的杆。</p>
     */
    public static int linkRange(int componentSupplyRange, int configuredAutoConnectRange) {
        return Math.max(componentSupplyRange, configuredAutoConnectRange);
    }

    /**
     * 视觉连线用的包围盒相交判定，语义与本体 {@code SimplePowerGrid#isOverlap} 完全一致：
     * 各轴半宽为 {@code range + 0.5}，仅接触（相切）不算相交。
     *
     * <p><b>这里必须传「连接范围」而不是「供电范围」。</b>供电范围只决定用电器能否接入，
     * 杆与杆之间的组网靠的是更大的连接范围；若视觉层误用供电范围，
     * 就会出现「电网明明连上了、线却不画」——而且只在杆数超过 2 时暴露，
     * 因为恰好两根杆走的是本体「无条件连一条」的特例。</p>
     */
    public static boolean visualLinkOverlap(
        double ax, double ay, double az, int rangeA,
        double bx, double by, double bz, int rangeB
    ) {
        return ax - rangeA - 0.5 < bx + rangeB + 0.5
               && ax + rangeA + 0.5 > bx - rangeB - 0.5
               && ay - rangeA - 0.5 < by + rangeB + 0.5
               && ay + rangeA + 0.5 > by - rangeB - 0.5
               && az - rangeA - 0.5 < bz + rangeB + 0.5
               && az + rangeA + 0.5 > bz - rangeB - 0.5;
    }

    /**
     * 判断一根输电杆能否并入既有电网。
     *
     * @param candidateSupply 候选杆的供电框
     * @param candidateLink   候选杆的连接框
     * @param gridSupply      电网中所有元件的供电框
     * @param gridLink        电网中所有中继元件的连接框
     * @return 满足供电相交或中继连接相交即为 true
     */
    public static boolean canTransmitterJoin(
        Box candidateSupply,
        Box candidateLink,
        Collection<Box> gridSupply,
        Collection<Box> gridLink
    ) {
        for (Box box : gridSupply) {
            if (candidateSupply.intersects(box)) return true;
        }
        for (Box box : gridLink) {
            if (candidateLink.intersects(box)) return true;
        }
        return false;
    }
}
