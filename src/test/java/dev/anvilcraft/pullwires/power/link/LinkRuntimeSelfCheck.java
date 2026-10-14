package dev.anvilcraft.pullwires.power.link;

/**
 * {@link LinkRuntime} 的纯逻辑自检。
 *
 * <p>净流出窗口是亏电断路的排序依据，衰减行为必须被钉死：一条曾经大量输出、
 * 后来不再输出的链路，平均值必须自然降下去，否则它会被永远判定为「值得断开的负担」。</p>
 */
public final class LinkRuntimeSelfCheck {
    private static int assertions;
    private static int failures;

    private LinkRuntimeSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        assertions = 0;
        failures = 0;

        LinkRuntime runtime = new LinkRuntime();
        expect("空窗口平均为 0", runtime.averageNetOut() == 0);

        runtime.push(100);
        expect("单次采样即为该值", runtime.averageNetOut() == 100);

        // 窗口容量 20：塞满之后平均仍是 100
        for (int i = 0; i < 25; i++) runtime.push(100);
        expect("持续输出时平均稳定", runtime.averageNetOut() == 100);

        // 之后不再输出，平均值应当逐拍衰减到 0
        for (int i = 0; i < 19; i++) runtime.push(0);
        expect("停止输出后平均值下降", runtime.averageNetOut() < 100);
        expect("停止输出后平均值仍为正", runtime.averageNetOut() > 0);
        for (int i = 0; i < 5; i++) runtime.push(0);
        expect("窗口滚完后平均值归零", runtime.averageNetOut() == 0);

        // 反向流动（主端是接收方）记负数。先清空窗口，避免被残留的零稀释。
        runtime.resetWindow();
        runtime.push(-30);
        expect("反向流动记负数", runtime.averageNetOut() == -30);
        runtime.resetWindow();
        expect("重置后窗口为空", runtime.averageNetOut() == 0);

        // 冷却：只能减到 0，不会变负
        runtime.setCooldownTicks(100);
        runtime.tickCooldown(20);
        expect("冷却按步长递减", runtime.cooldownTicks() == 80);
        runtime.tickCooldown(1000);
        expect("冷却不会变负", runtime.cooldownTicks() == 0);
        runtime.setCooldownTicks(-5);
        expect("设置负值被夹到 0", runtime.cooldownTicks() == 0);

        if (failures == 0) {
            System.out.println("[pullwires] LinkRuntime ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] LinkRuntime FAILED: " + failures + "/" + assertions);
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
