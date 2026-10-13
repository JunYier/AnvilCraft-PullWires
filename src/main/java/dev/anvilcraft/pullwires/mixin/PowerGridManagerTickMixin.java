package dev.anvilcraft.pullwires.mixin;

import dev.anvilcraft.pullwires.power.link.PowerInterconnectManager;
import dev.dubhe.anvilcraft.api.power.PowerGridManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code PowerGridManager#tick} 的首尾插入链路互联的结算。
 *
 * <p>放在管理器的首尾而不是自己的服务端 tick 监听器里，是为了<b>不依赖模组加载顺序</b>：
 * 本体的监听器在模组构造期注册，我们后加载，顺序虽然通常正确，
 * 但挂在本体方法上才是确定性的。</p>
 *
 * <p>结算发生在所有电网都 tick 完之后，因此读到的是本拍刚算出来的余量；
 * 结果供下一拍使用（一拍延迟，见 {@link PowerInterconnectManager}）。</p>
 */
@Mixin(PowerGridManager.class)
public abstract class PowerGridManagerTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void pullwires$beginSettlement(CallbackInfo callback) {
        PowerInterconnectManager.beginTick();
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void pullwires$settleInterconnect(CallbackInfo callback) {
        PowerInterconnectManager.settle();
    }
}
