package dev.anvilcraft.pullwires.power.link;

import java.util.Arrays;

/**
 * 定长滑动窗口的平均值。
 *
 * <p>纯整数运算，不依赖任何游戏类型，可脱离游戏单独验证。</p>
 *
 * <h2>为什么要有这个类</h2>
 * <p>「最近一段时间的平均」在本模组里已经有三处用途，而每一处都需要同样的东西：
 * 环形缓冲、按拍采样、未填满时按已填样本数求平均、以及<b>未成交的拍要记 0 让窗口自然衰减</b>。
 * 这些细节在 {@link LinkRuntime} 里已经写对过一次（亏电断路按它排序），
 * 再抄第二遍、第三遍迟早会漏掉「衰减」那一条——而漏掉它的后果很隐蔽：
 * 一条曾经大量输出的链路会被永远判定为「值得断开」。</p>
 *
 * <h2>为什么平均值而不是瞬时值</h2>
 * <p>判据用瞬时值会在边界上来回震荡：抖一下就到阈值以上，于是批准；
 * 批准之后又立刻不足，于是再断。用窗口平均值相当于<b>要求「你最近一直维持这个水平」</b>，
 * 这种抖动自然被平滑掉了。</p>
 */
public final class RollingAverage {
    /** 采样数（每拍一次），与 {@link LinkRuntime} 的窗口保持一致。 */
    public static final int DEFAULT_WINDOW_SIZE = 20;

    private final int[] samples;
    private int cursor;
    private int filled;

    public RollingAverage() {
        this(DEFAULT_WINDOW_SIZE);
    }

    /**
     * @param windowSize 采样数，必须为正
     */
    public RollingAverage(int windowSize) {
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be positive");
        this.samples = new int[windowSize];
    }

    /**
     * 记录本拍的采样值。
     *
     * <p><b>没有样本的拍也必须调用并传 0</b>：否则窗口不会滚动，
     * 一次历史高点会让平均值永远降不下去。</p>
     */
    public void push(int value) {
        this.samples[this.cursor] = value;
        this.cursor = (this.cursor + 1) % this.samples.length;
        if (this.filled < this.samples.length) this.filled++;
    }

    /** 窗口内已填样本的平均值；还没有样本时返回 0。 */
    public int average() {
        if (this.filled == 0) return 0;
        int sum = 0;
        for (int i = 0; i < this.filled; i++) {
            sum += this.samples[i];
        }
        return sum / this.filled;
    }

    /** 已填样本数，用于区分「窗口还没热」和「平均值恰好是 0」。 */
    public int sampleCount() {
        return this.filled;
    }

    /** 窗口是否已经填满（即平均值已经不再受启动期影响）。 */
    public boolean isFull() {
        return this.filled >= this.samples.length;
    }

    public void reset() {
        Arrays.fill(this.samples, 0);
        this.cursor = 0;
        this.filled = 0;
    }
}
