package dev.anvilcraft.pullwires.power.link;

/**
 * {@link PoleRuntime} 与 {@link PoleConfig} 的纯逻辑自检。
 *
 * <p>这几条是 M3 里最容易写错、又最难在游戏里复现的部分：开关跃变只认 OFF→ON、
 * 首次观察不能误判、优先级必须被夹在 0–15。</p>
 */
public final class PoleRuntimeSelfCheck {
    private PoleRuntimeSelfCheck() {
    }

    public static void main(String[] args) {
        System.exit(run() == 0 ? 0 : 1);
    }

    /** @return 失败的断言数，0 表示全部通过 */
    public static int run() {
        int assertions = 0;
        int failures = 0;

        // 首次观察不是跃变：刚读档时没有上一次的值。
        PoleRuntime runtime = new PoleRuntime();
        assertions++;
        if (runtime.observeSwitch(true)) {
            failures++;
            System.out.println("[pullwires] FAIL 首次观察到 ON 不应视为开关重启");
        }

        // 持续 ON 不是跃变。
        assertions++;
        if (runtime.observeSwitch(true)) {
            failures++;
            System.out.println("[pullwires] FAIL 持续 ON 不应视为开关重启");
        }

        // ON → OFF 不是跃变（是断电，链路应当进入 INACTIVE）。
        assertions++;
        if (runtime.observeSwitch(false)) {
            failures++;
            System.out.println("[pullwires] FAIL ON → OFF 不应视为开关重启");
        }

        // OFF → ON 是跃变（开关重启：重置运行时值 + 恢复链路）。
        assertions++;
        if (!runtime.observeSwitch(true)) {
            failures++;
            System.out.println("[pullwires] FAIL OFF → ON 应当视为开关重启");
        }

        // 重置计数器不得把 lastSwitch 也清掉，否则下一 tick 会被误判成又一次重启。
        runtime.setDeficitTicks(300);
        runtime.setReconnectCooldownTicks(100);
        runtime.setRequestTicks(50);
        runtime.resetCounters();
        assertions++;
        if (runtime.deficitTicks() != 0
            || runtime.reconnectCooldownTicks() != 0
            || runtime.requestTicks() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 重置后计数器应全部归零");
        }
        assertions++;
        if (runtime.lastSwitch() == null || !runtime.lastSwitch()) {
            failures++;
            System.out.println("[pullwires] FAIL 重置不应清掉 lastSwitch");
        }
        assertions++;
        if (runtime.observeSwitch(true)) {
            failures++;
            System.out.println("[pullwires] FAIL 重置后持续 ON 不应再被判为重启");
        }

        // 计数器不接受负值。
        runtime.setDeficitTicks(-10);
        assertions++;
        if (runtime.deficitTicks() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 亏电计时器不应接受负值");
        }

        // 优先级必须夹在 0–15。
        assertions++;
        if (PolePriority.clamp(-1) != PolePriority.MIN
            || PolePriority.clamp(99) != PolePriority.MAX
            || PolePriority.clamp(7) != 7) {
            failures++;
            System.out.println("[pullwires] FAIL 优先级应被夹在 0–15");
        }

        // 默认值就是文档里写的 7。
        assertions++;
        if (PolePriority.DEFAULT != 7) {
            failures++;
            System.out.println("[pullwires] FAIL 默认优先级应为 7");
        }

        // 配置区间必须能容纳默认值。
        assertions++;
        if (PolePriority.DEFAULT < PolePriority.MIN
            || PolePriority.DEFAULT > PolePriority.MAX) {
            failures++;
            System.out.println("[pullwires] FAIL 默认优先级应落在合法区间内");
        }

        if (failures == 0) {
            System.out.println("[pullwires] PoleRuntime ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] PoleRuntime FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }
}
