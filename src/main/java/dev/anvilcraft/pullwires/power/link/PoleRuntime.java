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
     * 记录本 tick 观察到的开关状态。
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

    /** 清空计数器与计时器，但保留 {@link #lastSwitch}——它必须反映本 tick 的观察结果。 */
    public void resetCounters() {
        this.deficitTicks = 0;
        this.reconnectCooldownTicks = 0;
        this.requestTicks = 0;
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
