package dev.anvilcraft.pullwires.power.link;

/**
 * {@link RollingAverage} 的纯逻辑自检。
 *
 * <p>重点是那两条容易被漏掉的语义：<b>未填满时按已填样本数求平均</b>（而不是除以窗口长度）、
 * 以及<b>必须靠持续传 0 让窗口衰减</b>（否则一次历史高点会把平均值永远托住）。</p>
 */
public final class RollingAverageSelfCheck {
    private static int assertions;
    private static int failures;

    private RollingAverageSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        assertions = 0;
        failures = 0;

        RollingAverage window = new RollingAverage();
        expect("空窗口平均为 0", window.average() == 0);
        expect("空窗口样本数为 0", window.sampleCount() == 0);
        expect("空窗口未填满", !window.isFull());

        window.push(100);
        expect("单个样本即为该值（不是 100/20）", window.average() == 100);
        expect("样本数随采样增长", window.sampleCount() == 1);

        // 填满窗口：25 次 100 之后平均仍是 100
        for (int i = 0; i < 24; i++) window.push(100);
        expect("填满后平均稳定", window.average() == 100);
        expect("此时窗口已填满", window.isFull());
        expect("样本数不超过窗口长度", window.sampleCount() == RollingAverage.DEFAULT_WINDOW_SIZE);

        // 关键：靠持续传 0 衰减，窗口滚完就归零
        for (int i = 0; i < 19; i++) window.push(0);
        expect("停止采样后平均值下降", window.average() < 100);
        expect("停止采样后平均值仍为正", window.average() > 0);
        for (int i = 0; i < 5; i++) window.push(0);
        expect("窗口滚完后平均值归零", window.average() == 0);

        // 反向的样本（负余额）也要能平均
        window.reset();
        expect("重置后样本数归零", window.sampleCount() == 0 && window.average() == 0);
        window.push(-80);
        window.push(-20);
        expect("负数样本求平均", window.average() == -50);

        // 窗口长度 1：立刻生效，也立刻衰减
        RollingAverage single = new RollingAverage(1);
        single.push(7);
        expect("长度 1 的窗口即刻生效", single.average() == 7);
        single.push(0);
        expect("长度 1 的窗口即刻衰减", single.average() == 0);

        // 非法窗口长度必须当场报错，而不是留到运行时算出诡异结果
        boolean threw = false;
        try {
            new RollingAverage(0);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        expect("窗口长度必须为正", threw);

        if (failures == 0) {
            System.out.println("[pullwires] RollingAverage ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] RollingAverage FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }

    private static void expect(String name, boolean condition) {
        assertions++;
        if (condition) return;
        failures++;
        System.out.println("[pullwires] FAIL " + name);
    }
}
