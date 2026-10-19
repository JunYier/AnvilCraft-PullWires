package dev.anvilcraft.pullwires.power.link;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 杆顶锚点：本模组所有「从杆顶算起」的位置都必须走这一处。
 *
 * <p>用途分散在三个地方，而它们必须给出**同一个点**，否则会出现「Boss 条读数与粒子束两端对不上」：</p>
 * <ul>
 *   <li>服务端 {@code ManualLinkSessions} 算玩家眼睛到主端的距离（Boss 条与超距自动解绑）；</li>
 *   <li>客户端 {@code ManualLinkAim} 算放置预览与粒子束的落点；</li>
 *   <li>客户端 {@code ManualLinkClientEvents} 画绑定提示粒子束。</li>
 * </ul>
 *
 * <p>锚点的定义：{@code PoleKey.pos()} 里存的一律是<b>顶段</b>（所有构造入口先过
 * {@code PoleResolver.resolveTop}），所以顶段方块的上表面中心就是「杆顶」——
 * 只需沿 y 轴加一格，<b>不需要知道整根杆是三格还是四格</b>。</p>
 *
 * <p>放这里而不是客户端类里：服务端也要用，而服务端不能碰客户端类型。</p>
 */
public final class PoleAnchor {
    /** 顶段方块顶面中心相对其底面中心的偏移：恰好一格。 */
    public static final double TOP_OFFSET = 1.0;

    private PoleAnchor() {
    }

    /** 任意坐标上方 {@code heightOffset} 格处的锚点。 */
    public static Vec3 headOf(BlockPos pos, double heightOffset) {
        return Vec3.atBottomCenterOf(pos).add(0.0, heightOffset, 0.0);
    }

    /** 顶段方块（{@code PoleKey.pos()}）的杆顶。 */
    public static Vec3 masterHead(BlockPos topSegment) {
        return headOf(topSegment, TOP_OFFSET);
    }
}
