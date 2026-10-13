package dev.anvilcraft.pullwires.power.link;

/**
 * 结算优先级的取值范围与默认值。
 *
 * <p>刻意独立成一个<b>零依赖</b>的类：{@link PoleConfig} 带有 {@code Codec} 字段，
 * 一旦被测试引用就会把 Mojang 的 serialization 库拖进测试运行时，
 * 而这里的逻辑本身完全不需要它。</p>
 *
 * <p>语义：**越低越慷慨**——优先级低的网络在互联池里更早给出电力、更晚获得补电。</p>
 */
public final class PolePriority {
    public static final int MIN = 0;
    public static final int MAX = 15;
    /** 新放置输电杆的默认优先级。 */
    public static final int DEFAULT = 7;

    private PolePriority() {
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
