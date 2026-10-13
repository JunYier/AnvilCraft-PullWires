package dev.anvilcraft.pullwires.power.link;

/**
 * 手动连接的距离判定规则。
 *
 * <p>纯逻辑，不依赖游戏类型。客户端用它决定引导带的颜色，服务端用它决定放行、拒绝还是放弃，
 * 两边共用同一份实现，避免出现「看着在范围内、放下却被拒绝」这类不一致。</p>
 *
 * <p>距离一律取<b>直线距离</b>（顶端中心之间），不使用包围盒判定——
 * 本体原有的 {@code isOverlap} 是轴对齐盒相交，对角方向会比直线距离宽松得多。</p>
 */
public final class ManualLinkRule {
    private ManualLinkRule() {
    }

    /**
     * 判断某个距离所处的区间。
     *
     * @param distance         瞄准点（或放置点）到主端顶端的直线距离
     * @param connectRange     手动连接范围
     * @param cancelMultiplier 放弃距离倍数，超过 {@code connectRange * cancelMultiplier} 即放弃
     */
    public static ManualLinkFeedback feedback(double distance, int connectRange, double cancelMultiplier) {
        if (distance > connectRange * cancelMultiplier) return ManualLinkFeedback.CANCEL;
        return distance <= connectRange ? ManualLinkFeedback.IN_RANGE : ManualLinkFeedback.OUT_OF_RANGE;
    }
}
