package dev.anvilcraft.pullwires.power.link;

/**
 * 一次连接尝试的准入规则。
 *
 * <p><b>纯逻辑，不引用任何游戏类型</b>：这样可以脱离游戏运行环境自检——而这条规则恰恰最需要自检，
 * 因为它放宽了两种限制（距离、维度），一旦写错就是「凭空连接」。</p>
 *
 * <h2>两条放宽规则的边界（M10）</h2>
 * <ul>
 *   <li><b>距离</b>：以<b>主端</b>的种类为准。跨维度杆无距离限制（同维度、跨维度都一样）；
 *       其余杆型一律受主端 {@code manualConnectRange} 约束。</li>
 *   <li><b>维度</b>：只有<b>两端都是跨维度杆</b>、且配置开关打开时才允许跨维度。
 *       放宽到「有一端是就行」，等于随便一根普通杆配一根跨维度杆就能白嫖跨维度输送。</li>
 * </ul>
 *
 * <p>另外，两端不在同一维度时距离本身没有意义（坐标不在同一坐标系里），因此<b>不做</b>距离判定——
 * 这是「跨维度时不判距离」而不是「跨维度时距离无限」。</p>
 */
public final class CrossDimensionRule {
    private CrossDimensionRule() {
    }

    /** 一次连接尝试的判定结果。 */
    public enum Verdict {
        /** 放行，建立链路。 */
        ALLOW,
        /** 两端跨维度，但不满足「两根跨维度杆 + 开关打开」。 */
        CROSS_DIMENSION_DENIED,
        /** 同维度，但超出手动连接范围。 */
        TOO_FAR
    }

    /**
     * 判定一次连接尝试。
     *
     * @param acrossDimensions       两端是否位于不同维度
     * @param masterCrossDimensional 主端是不是跨维度杆
     * @param targetCrossDimensional 目标是不是跨维度杆
     * @param enabled                配置里的跨维度开关
     * @param distance               两端直线距离；仅在<b>同维度</b>时有意义
     * @param range                  主端的手动连接范围
     */
    public static Verdict verdict(
        boolean acrossDimensions,
        boolean masterCrossDimensional,
        boolean targetCrossDimensional,
        boolean enabled,
        double distance,
        int range
    ) {
        if (acrossDimensions) {
            boolean allowed = enabled && masterCrossDimensional && targetCrossDimensional;
            return allowed ? Verdict.ALLOW : Verdict.CROSS_DIMENSION_DENIED;
        }
        // 同维度：跨维度杆没有距离限制（需求 AC3.2.3）。
        if (masterCrossDimensional) return Verdict.ALLOW;
        return distance <= range ? Verdict.ALLOW : Verdict.TOO_FAR;
    }
}
