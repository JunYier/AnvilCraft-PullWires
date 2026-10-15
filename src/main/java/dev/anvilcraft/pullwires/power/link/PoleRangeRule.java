package dev.anvilcraft.pullwires.power.link;

/**
 * 三档范围的换算规则。
 *
 * <h2>为什么配置里存的是倍数而不是方块数</h2>
 * <p>本体的供电范围 R 是<b>玩家可改</b>的（{@code machines.powerTransmitterRange} 默认 8）。
 * 若我们把 {@code R_auto} / {@code R_manual} 写死成方块数，玩家一调大本体 R，
 * 就会出现 {@code R_supply > R_auto} 的越界——旧存档里已经放好的杆会因此报错。
 * 写成「R 的倍数」之后，三档的嵌套关系是<b>结构性</b>的：本体 R 怎么改，关系都不破。</p>
 *
 * <p>纯逻辑，不依赖任何游戏类型，因此可以脱离游戏运行环境自检。</p>
 */
public final class PoleRangeRule {
    private PoleRangeRule() {
    }

    /**
     * 换算结果（单位：格）。
     *
     * @param supplyRange      该杆型自身的供电范围；本体那两种杆由本体配置给出，本模组自己的两种由本模组配置给出
     * @param autoConnectRange 自动连接范围；0 表示不参与自动组网
     * @param manualConnectRange 手动（绑定）连接范围；0 表示禁用
     */
    public record Ranges(int supplyRange, int autoConnectRange, int manualConnectRange) {
    }

    /**
     * 把倍数换算成三档范围。
     *
     * <p><b>{@code R_auto ≤ R_manual} 必须恒成立</b>：两个倍数各自独立可调，
     * 玩家完全可能把 manual 的倍数调得比 auto 还小。这里以 auto 为下限收敛，不让调用方各自兜底。</p>
     *
     * @param supplyRange      该杆型自身的供电范围
     * @param base             倍数基准，即「本体 R」
     * @param autoMultiplier   autoR 的倍数（0 = 不自动组网）
     * @param manualMultiplier manualR 的倍数（0 = 禁用）
     */
    public static Ranges resolve(
        int supplyRange, int base, double autoMultiplier, double manualMultiplier
    ) {
        int autoConnectRange = scale(base, autoMultiplier);
        int manualConnectRange = Math.max(scale(base, manualMultiplier), autoConnectRange);
        return new Ranges(supplyRange, autoConnectRange, manualConnectRange);
    }

    /**
     * 倍数 → 实际格数。
     *
     * <p>倍数或基准为 0 时返回 0：这是「该档禁用」的表示法（跨维度杆的 autoR / manualR 就是 0）。
     * 否则四舍五入且至少 1 格——配置里写 0.01 也应该得到「能连 1 格」，而不是静默变成禁用。</p>
     */
    public static int scale(int base, double multiplier) {
        if (base <= 0 || !(multiplier > 0.0)) return 0; // !(x > 0) 同时挡住 NaN
        return Math.max(1, (int) Math.round(base * multiplier));
    }
}
