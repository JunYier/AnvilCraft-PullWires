package dev.anvilcraft.pullwires.power.link;

/**
 * 配置页优先级滑条的纯数学：{@code 0–15} 整数 ⇄ 滑条比例 {@code [0,1]}，以及松手时要发的增量。
 *
 * <p>刻意做成一个<b>零依赖</b>的类：它不碰任何 Minecraft / GUI 类型，
 * 也不碰 {@code com.mojang.serialization}，因此可以脱离游戏在 {@code verifyAddon} 里断言。
 * 页面只负责把鼠标位置交给这里换算，服务端仍会在权威配置上再夹一次。</p>
 */
public final class PrioritySliderMath {
    private PrioritySliderMath() {
    }

    /** 优先级 → 滑条比例。越界值先夹到 0–15。 */
    public static double proportionOf(int priority) {
        int clamped = PolePriority.clamp(priority);
        return (clamped - PolePriority.MIN) / (double) (PolePriority.MAX - PolePriority.MIN);
    }

    /** 滑条比例 → 优先级（四舍五入，再夹到 0–15）。 */
    public static int priorityAt(double proportion) {
        double clamped = Math.max(0.0D, Math.min(1.0D, proportion));
        int value = PolePriority.MIN
            + (int) Math.round(clamped * (PolePriority.MAX - PolePriority.MIN));
        return PolePriority.clamp(value);
    }

    /**
     * 从当前镜像值到滑条目标值的增量。
     *
     * <p>包只发增量：两端都先夹取，因此客户端算出来的只是「相对镜像差多少」，
     * 镜像过期最多导致一次空操作，不会写出越界值。</p>
     */
    public static int deltaFor(int target, int current) {
        return PolePriority.clamp(target) - PolePriority.clamp(current);
    }
}
