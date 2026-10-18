package dev.anvilcraft.pullwires.power.link;

import java.util.Arrays;

/**
 * 单条链路的运行期数据（<b>不持久化</b>）。
 *
 * <p>链路定义是永久的，而「最近给出去多少电」「挂起后冷却了多久」是纯运行时状态，
 * 重启后应当从零重新观察——把两者混在一起会让存档携带过期的洞察。</p>
 *
 * <h2>为什么每拍记两个数而不是一个</h2>
 * <p>跨维度链路有损耗：主端确实<b>失去</b>了 512，而从端只<b>收到</b> 384，差额被耗散掉了。
 * 这两个数各自都有正确的用途，谁也不能替代谁：</p>
 * <ul>
 *   <li>{@code sent}：主端口径。tooltip 的「外送电量」用它；亏电断路按它排序——
 *       主端失去的就是这么多，与对端拿到多少无关；</li>
 *   <li>{@code received}：从端口径。tooltip 的「内收电量」用它。</li>
 * </ul>
 * <p>同维度链路的两者恒等，跨维度时差值是损耗。</p>
 */
public final class LinkRuntime {
    /** 滑动窗口的采样数（每拍结算采样一次）。 */
    private static final int WINDOW_SIZE = 20;

    private final int[] window = new int[WINDOW_SIZE];
    private int cursor;
    private int filled;

    /** 本拍主端送出的量，供 tooltip 的「外送电量」与断路排序读取。 */
    private int lastSent;

    /** 本拍从端收到的量，供 tooltip 的「内收电量」读取。跨维度时小于 {@link #lastSent}。 */
    private int lastReceived;

    /** 挂起后的重连冷却（tick）。 */
    private int cooldownTicks;

    /**
     * 记录本拍这条链路的转移量。
     *
     * <p><b>窗口里存的是 {@code sent}</b>：亏电断路关心「主端为了养这条链路要付出多少」，
     * 而不是对端实际拿到多少。损耗是主端的真实支出，不能从窗口里扣掉。</p>
     *
     * <p>未成交的拍必须记 {@code (0, 0)}，这样窗口会自动衰减：一条曾经大量输出、
     * 后来不再输出的链路，其平均值会自然降下去，不会永远被判定为「值得断开的负担」。</p>
     *
     * @param sent     主端送出的量，非负
     * @param received 从端收到的量，非负且 ≤ {@code sent}
     */
    public void push(int sent, int received) {
        this.window[this.cursor] = sent;
        this.cursor = (this.cursor + 1) % WINDOW_SIZE;
        if (this.filled < WINDOW_SIZE) this.filled++;
        // 这两个赋值曾经漏掉过（窗口写了、last 没写），结果「外送 / 内收电量」两行永远是 0
        // 因而被 tooltip 静默隐藏。自检现在覆盖它们。
        this.lastSent = sent;
        this.lastReceived = received;
    }

    /** 窗口内的平均送出量，用于亏电断路时的排序依据。 */
    public int averageNetOut() {
        if (this.filled == 0) return 0;
        int sum = 0;
        for (int i = 0; i < this.filled; i++) {
            sum += this.window[i];
        }
        return sum / this.filled;
    }

    public int lastSent() {
        return this.lastSent;
    }

    public int lastReceived() {
        return this.lastReceived;
    }

    /** 本拍被耗散掉的量（跨维度损耗）；同维度恒为 0。 */
    public int lastLost() {
        return Math.max(0, this.lastSent - this.lastReceived);
    }

    public void resetWindow() {
        Arrays.fill(this.window, 0);
        this.lastSent = 0;
        this.lastReceived = 0;
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
