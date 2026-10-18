package dev.anvilcraft.pullwires.power.link;

/**
 * {@link LinkRuntime} 的纯逻辑自检。
 *
 * <h2>窗口</h2>
 * <p>净流出窗口是亏电断路的排序依据，衰减行为必须被钉死：一条曾经大量输出、
 * 后来不再输出的链路，平均值必须自然降下去，否则它会被永远判定为「值得断开的负担」。</p>
 *
 * <h2>last* 两个字段</h2>
 * <p>这两个字段曾经<b>漏掉赋值</b>：{@code push} 只写窗口、从不写 last，
 * 于是「外送 / 内收电量」永远是 0、被 tooltip 静默隐藏，而窗口相关的一切都正常——
 * 所以自检当时全绿也没暴露它。现在把它们逐条钉住。</p>
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

        runtime.push(100, 100);
        expect("单次采样即为该值", runtime.averageNetOut() == 100);

        // 窗口容量 20：塞满之后平均仍是 100
        for (int i = 0; i < 25; i++) runtime.push(100, 100);
        expect("持续输出时平均稳定", runtime.averageNetOut() == 100);

        // 之后不再输出，平均值应当逐拍衰减到 0
        for (int i = 0; i < 19; i++) runtime.push(0, 0);
        expect("停止输出后平均值下降", runtime.averageNetOut() < 100);
        expect("停止输出后平均值仍为正", runtime.averageNetOut() > 0);
        for (int i = 0; i < 5; i++) runtime.push(0, 0);
        expect("窗口滚完后平均值归零", runtime.averageNetOut() == 0);

        // ★ push 必须同时写窗口与 last*：只写窗口正是那个漏赋值的形态。
        runtime.resetWindow();
        runtime.push(512, 384);
        expect("push 写入本拍送出量", runtime.lastSent() == 512);
        expect("push 写入本拍收到量", runtime.lastReceived() == 384);
        expect("窗口用送出量而非收到量", runtime.averageNetOut() == 512);

        // 损耗 = 送出 − 收到（仅跨维度会发生）。
        expect("损耗等于两者之差", runtime.lastLost() == 128);
        runtime.push(100, 100);
        expect("同维度时无损耗", runtime.lastLost() == 0);

        // 未成交的拍必须把 last* 归零，否则 tooltip 会一直显示上一拍的旧值。
        runtime.push(0, 0);
        expect("未成交时送出量归零", runtime.lastSent() == 0);
        expect("未成交时收到量归零", runtime.lastReceived() == 0);

        // resetWindow 也要清 last*，否则挂起/重启后仍会读到过期的量。
        runtime.push(200, 150);
        runtime.resetWindow();
        expect("重置后窗口为空", runtime.averageNetOut() == 0);
        expect("重置后送出量归零", runtime.lastSent() == 0);
        expect("重置后收到量归零", runtime.lastReceived() == 0);
        expect("重置后损耗归零", runtime.lastLost() == 0);

        // 收到量永远不该大于送出量（否则 lastLost 的语义就反了）。
        runtime.push(64, 64);
        expect("同维度时两者相等", runtime.lastSent() == runtime.lastReceived());

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
