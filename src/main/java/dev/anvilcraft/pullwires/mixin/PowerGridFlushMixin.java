package dev.anvilcraft.pullwires.mixin;

import dev.anvilcraft.pullwires.power.link.PowerInterconnectManager;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把链路互联的结算结果接回电网。
 *
 * <p>{@code PowerGrid#flush()} 每次都会把 {@code generate}/{@code consume} 从零重算，
 * 所以在它的尾部记录「自有余量」，再叠加一次本模组算出的净进口/净出口，
 * 就等价于给电网接了一条可双向流动的联络线——而且完全没有往元件集合里塞东西。</p>
 *
 * <p>{@code flush()} 有若干提前返回（某个元件已被移除时），那些路径会跳过本注入。
 * 影响仅是那一拍不叠加增量，下一拍自动恢复，代价可接受。</p>
 */
@Mixin(PowerGrid.class)
public abstract class PowerGridFlushMixin {
    @Shadow
    private int generate;

    @Shadow
    private int consume;

    @Inject(method = "flush", at = @At("TAIL"), require = 0)
    private void pullwires$applyInterconnectDelta(CallbackInfoReturnable<Boolean> callback) {
        PowerGrid grid = (PowerGrid) (Object) this;
        PowerInterconnectManager.recordRawBalance(grid, this.generate - this.consume);

        int delta = PowerInterconnectManager.deltaFor(grid);
        if (delta == 0) return;
        if (delta > 0) {
            // 净进口：抬高发电侧，使缺口被填平、isWorking() 转真。
            this.generate += delta;
        } else {
            // 净出口：抬高耗电侧，把送出去的功率从本网账上扣掉。
            this.consume -= delta;
        }
    }
}
