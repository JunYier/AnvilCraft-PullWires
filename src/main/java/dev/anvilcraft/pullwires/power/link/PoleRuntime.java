package dev.anvilcraft.pullwires.power.link;

import javax.annotation.Nullable;

/**
 * 一根输电杆的运行时状态，<b>不写入存档</b>。
 *
 * <p>刻意做成不依赖任何游戏类型的普通类，方便单独验证开关跃变检测。
 * 存档只保存 {@link PoleConfig}（优先级、锁定），运行时值每次开机从零开始，
 * 这样不会出现「读档瞬间就因为历史亏电计时把链路全断掉」。</p>
 */
public final class PoleRuntime {
    private @Nullable Boolean lastSwitch;
    private int deficitTicks;
    private int reconnectCooldownTicks;
    private int requestTicks;

    /**
     * 本杆所在电网余额的滑动窗口（每拍采样一次）。
     *
     * <p><b>一个窗口供应两个角色</b>：主端看它的正值部分当「平均富余」，从端看它的负值部分当
     * 「平均缺口」。所以「富余」与「申请量」不需要各开一份状态——这也正是重连裁决
     * （{@link ReconnectPolicy#approve}）两边都取平均的来源。</p>
     */
    private final RollingAverage balanceWindow = new RollingAverage();

    /**
     * 记录本拍观察到的开关状态。
     *
     * @param switchOn 当前 {@code SWITCH} 是否为 ON
     * @return 是否发生了 <b>OFF → ON 跃变</b>（即「开关重启」，应当重置运行时值并恢复链路）
     */
    public boolean observeSwitch(boolean switchOn) {
        // 首次观察不算跃变：刚读档时没有上一次的值，不能误判成一次重启。
        boolean restart = this.lastSwitch != null && !this.lastSwitch && switchOn;
        this.lastSwitch = switchOn;
        return restart;
    }

    /** 记录本拍的电网余额（正数富余、负数缺口），供重连裁决取平均。 */
    public void recordBalance(int balance) {
        this.balanceWindow.push(balance);
    }

    /** 最近一段时间的<b>平均富余</b>；一直亏电时为 0。 */
    public int averageSurplus() {
        return Math.max(0, this.balanceWindow.average());
    }

    /** 最近一段时间的<b>平均缺口</b>（正数）；一直富余时为 0。 */
    public int averageDemand() {
        return Math.max(0, -this.balanceWindow.average());
    }

    /** 余额窗口是否已采到样本；还没热时不要用它做裁决，否则会被启动期的 0 误导。 */
    public boolean balanceWindowWarm() {
        return this.balanceWindow.sampleCount() > 0;
    }

    /**
     * 推进一次<b>重连申请计时</b>：从端被挂起后每拍调用一次。
     *
     * <p>计时器就是 {@link #requestTicks()}（不额外开状态）：按 {@code step}（电网的结算间隔）累加，
     * <b>够到 {@code interval} 就归零并报告一次申请</b>。旧实现是在同一个计数器上先比「稳定时长」
     * 再取模判周期，读数与语义纠缠在一起——现在只有「累加、到点归零」一条规则。</p>
     *
     * @param step     每拍推进的 tick 数（{@code PowerGrid.gridInterval()}）
     * @param interval 申请周期（tick），非正数按 1 处理
     * @return 本拍是否到点、应当向主端发一次申请
     */
    public boolean tickRequest(int step, int interval) {
        this.requestTicks += Math.max(0, step);
        if (this.requestTicks < Math.max(1, interval)) return false;
        this.requestTicks = 0;
        return true;
    }

    /** 清空计数器与计时器，但保留 {@link #lastSwitch}——它必须反映本 tick 的观察结果。 */
    public void resetCounters() {
        this.deficitTicks = 0;
        this.reconnectCooldownTicks = 0;
        this.requestTicks = 0;
        // 余额窗口也是运行时值：开关重启的意义就是「当作一次全新的开始」，历史余额不该留下。
        this.balanceWindow.reset();
    }

    public @Nullable Boolean lastSwitch() {
        return this.lastSwitch;
    }

    public int deficitTicks() {
        return this.deficitTicks;
    }

    public void setDeficitTicks(int deficitTicks) {
        this.deficitTicks = Math.max(0, deficitTicks);
    }

    public int reconnectCooldownTicks() {
        return this.reconnectCooldownTicks;
    }

    public void setReconnectCooldownTicks(int reconnectCooldownTicks) {
        this.reconnectCooldownTicks = Math.max(0, reconnectCooldownTicks);
    }

    public int requestTicks() {
        return this.requestTicks;
    }

    public void setRequestTicks(int requestTicks) {
        this.requestTicks = Math.max(0, requestTicks);
    }
}
