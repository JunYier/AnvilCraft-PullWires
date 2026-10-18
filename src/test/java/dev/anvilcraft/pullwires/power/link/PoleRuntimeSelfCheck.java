package dev.anvilcraft.pullwires.power.link;

/**
 * {@link PoleRuntime} 与 {@link PoleConfig} 的纯逻辑自检。
 *
 * <p>这几条是 M3 里最容易写错、又最难在游戏里复现的部分：开关跃变只认 OFF→ON、
 * 首次观察不能误判、优先级必须被夹在 0–15。V1.12.11 起还覆盖重连申请计时
 * （{@link PoleRuntime#tickRequest}：够一个周期才产生申请，产生后归零重新计时）。</p>
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

        // --- 余额窗口：一个窗口同时供应「主端平均富余」与「从端平均缺口」 ---
        PoleRuntime balance = new PoleRuntime();
        assertions++;
        if (balance.balanceWindowWarm() || balance.averageSurplus() != 0 || balance.averageDemand() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 未采样时窗口应当是冷的、两边平均都是 0");
        }

        balance.recordBalance(120);
        assertions++;
        if (balance.averageSurplus() != 120 || balance.averageDemand() != 0 || !balance.balanceWindowWarm()) {
            failures++;
            System.out.println("[pullwires] FAIL 富余 120 时：平均富余 120、平均缺口 0、窗口已热");
        }

        PoleRuntime deficit = new PoleRuntime();
        deficit.recordBalance(-60);
        assertions++;
        if (deficit.averageDemand() != 60 || deficit.averageSurplus() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 亏电 60 时：平均缺口 60、平均富余 0");
        }

        // 两者都取平均：忽正忽负的余额会被平滑，而不是被最后一拍决定
        PoleRuntime mixed = new PoleRuntime();
        mixed.recordBalance(100);
        mixed.recordBalance(-100);
        assertions++;
        if (mixed.averageSurplus() != 0 || mixed.averageDemand() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 正负相抵时两者都应为 0（取平均，不是最后一拍）");
        }

        // 开关重启会把余额窗口一起清掉：历史余额不该跨过一次「重新开始」
        balance.resetCounters();
        assertions++;
        if (balance.balanceWindowWarm() || balance.averageSurplus() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 重置后余额窗口应回到未热且平均归零");
        }

        // --- 重连申请计时：够一个周期才产生一次申请，产生后重新计时 ---
        // 周期 200、每拍推 20：前 9 拍（共 180 tick）一次申请都不该产生。
        PoleRuntime requester = new PoleRuntime();
        boolean firedEarly = false;
        for (int tick = 0; tick < 9; tick++) {
            firedEarly |= requester.tickRequest(20, 200);
        }
        assertions++;
        if (firedEarly || requester.requestTicks() != 180) {
            failures++;
            System.out.println("[pullwires] FAIL 未到申请周期不应产生申请，且计时器应停在 180");
        }

        // 第 10 拍刚好攒够 200 tick：产生一次申请并把计时器归零。
        assertions++;
        if (!requester.tickRequest(20, 200) || requester.requestTicks() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 攒够一个周期应产生一次申请并把计时器归零");
        }

        // 归零之后重新起算：再走 9 拍仍然不产生申请。
        boolean firedAgain = false;
        for (int tick = 0; tick < 9; tick++) {
            firedAgain |= requester.tickRequest(20, 200);
        }
        assertions++;
        if (firedAgain || requester.requestTicks() != 180) {
            failures++;
            System.out.println("[pullwires] FAIL 产生申请之后应重新计时，而不是每拍都申请");
        }

        // 周期不是步长整数倍时，在「够阈值」的那一拍产生申请（步长 30、周期 100）。
        PoleRuntime overshoot = new PoleRuntime();
        assertions++;
        if (overshoot.tickRequest(30, 100) || overshoot.requestTicks() != 30) {
            failures++;
            System.out.println("[pullwires] FAIL 步长 30 / 周期 100：第一拍不应产生申请");
        }
        overshoot.tickRequest(30, 100);
        overshoot.tickRequest(30, 100);
        assertions++;
        if (overshoot.requestTicks() != 90) {
            failures++;
            System.out.println("[pullwires] FAIL 步长 30 / 周期 100：三拍之后应为 90 tick");
        }
        assertions++;
        if (!overshoot.tickRequest(30, 100) || overshoot.requestTicks() != 0) {
            failures++;
            System.out.println("[pullwires] FAIL 步长 30 / 周期 100：第 120 tick 应产生申请并归零");
        }

        if (failures == 0) {
            System.out.println("[pullwires] PoleRuntime ok (" + assertions + " assertions)");
        } else {
            System.out.println("[pullwires] PoleRuntime FAILED: " + failures + "/" + assertions);
        }
        return failures;
    }
}
