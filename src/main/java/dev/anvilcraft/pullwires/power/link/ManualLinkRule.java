package dev.anvilcraft.pullwires.power.link;

/**
 * 手动连接的距离判定规则。
 *
 * <p>纯逻辑，不依赖游戏类型。客户端用它决定引导带的颜色，服务端用它决定放行、拒绝还是放弃，
 * 两边共用同一份实现，避免出现「看着在范围内、放下却被拒绝」这类不一致。</p>
 *
 * <p>距离一律取 {@link PowerLinkRule#boxDistance}（三轴分量的最大者，即「曼哈顿距离的长边长度」），
 * <b>不是</b>直线距离。理由：手动连接范围必须与自动连接范围同量纲才能保证三档嵌套，
 * 而「长边长度 ≤ R」正是包围盒判定的标量形式——用它可以<b>直接</b>比较，
 * 不需要把两端的半宽加起来做方向相关的换算。Boss 条、放置判定、引导带因此共用同一个数。</p>
 */
public final class ManualLinkRule {
    private ManualLinkRule() {
    }

    /**
     * 判断某个距离所处的区间。
     *
     * @param distance         瞄准点（或放置点）到主端顶端的<b>长边长度</b>
     *                         （见 {@link PowerLinkRule#boxDistance}）
     * @param connectRange     手动连接范围
     * @param cancelMultiplier 放弃距离倍数，超过 {@code connectRange * cancelMultiplier} 即放弃
     */
    public static ManualLinkFeedback feedback(double distance, int connectRange, double cancelMultiplier) {
        if (distance > connectRange * cancelMultiplier) return ManualLinkFeedback.CANCEL;
        return distance <= connectRange ? ManualLinkFeedback.IN_RANGE : ManualLinkFeedback.OUT_OF_RANGE;
    }

    /** Boss 条里绿色段占的比例；剩下的 1/4 是红色的「超出到断开」段。 */
    public static final float GREEN_SEGMENT_END = 0.75F;

    /**
     * 连接距离在 Boss 条上的进度。
     *
     * <p>前 {@link #GREEN_SEGMENT_END} 表示「还在手动连接范围内」，后 1/4 表示「已超出、正在逼近断开」。
     * <b>两段各自线性、段与段之间斜率不同</b>：绿色段每格走得慢，红色段走得快。
     * 单条 Boss 条即可表达，不需要两条——颜色在 3/4 处由绿转红即可。</p>
     *
     * <p>达到放弃距离后夹在 1，调用方会在那一刻清除待连接状态并移除 Boss 条。</p>
     */
    public static float barProgress(double distance, int connectRange, double cancelMultiplier) {
        if (distance <= 0) return 0.0F;
        if (connectRange <= 0) return 1.0F;

        double cancelDistance = connectRange * cancelMultiplier;
        if (distance >= cancelDistance) return 1.0F;

        if (distance <= connectRange) {
            return (float) (GREEN_SEGMENT_END * (distance / connectRange));
        }
        double overSpan = cancelDistance - connectRange;
        if (overSpan <= 0) return 1.0F;
        double overRatio = (distance - connectRange) / overSpan;
        return (float) (GREEN_SEGMENT_END + (1.0 - GREEN_SEGMENT_END) * overRatio);
    }
}
