package dev.anvilcraft.pullwires.power.link;

import java.util.Arrays;

/**
 * 单条链路的运行期数据（<b>不持久化</b>）。
 *
 * <p>链路定义是永久的，而「最近给出去多少电」「挂起后冷却了多久」是纯运行时状态，
 * 重启后应当从零重新观察——把两者混在一起会让存档携带过期的洞察。</p>
 */
public final class LinkRuntime {
    /** 滑动窗口的采样数（每拍结算采样一次）。 */
    private static final int WINDOW_SIZE = 20;

    private final int[] window = new int[WINDOW_SIZE];
    private int cursor;
    private int filled;

    /** 挂起后的重连冷却（tick）。 */
    private int cooldownTicks;

    /**
     * 记录本拍这条链路的净流出（主端 → 从端为正）。
     *
     * <p>未成交的拍记 0，这样窗口会自动衰减：一条曾经大量输出、后来不再输出的链路，
     * 其平均值会自然降下去，不会永远被判定为「值得断开的负担」。</p>
     */
    public void push(int netOut) {
        this.window[this.cursor] = netOut;
        this.cursor = (this.cursor + 1) % WINDOW_SIZE;
        if (this.filled < WINDOW_SIZE) this.filled++;
    }

    /** 窗口内的平均净流出，用于亏电断路时的排序依据。 */
    public int averageNetOut() {
        if (this.filled == 0) return 0;
        int sum = 0;
        for (int i = 0; i < this.filled; i++) {
            sum += this.window[i];
        }
        return sum / this.filled;
    }

    public void resetWindow() {
        Arrays.fill(this.window, 0);
        this.cursor = 0;
        this.filled = 0;
    }

    public int cooldownTicks() {
        return this.cooldownTicks;
    }

    public void setCooldownTicks(int value) {
        this.cooldownTicks = Math.max(0, value);
    }

    public void tickCooldown(int amount) {
        setCooldownTicks(this.cooldownTicks - amount);
    }
}
