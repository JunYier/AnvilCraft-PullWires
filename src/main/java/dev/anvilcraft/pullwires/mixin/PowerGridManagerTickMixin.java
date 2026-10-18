package dev.anvilcraft.pullwires.mixin;

import dev.anvilcraft.pullwires.power.link.PowerInterconnectManager;
import dev.dubhe.anvilcraft.api.power.PowerGridManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code PowerGridManager#tick} 的尾部插入链路互联的结算。
 *
 * <p>放在管理器的尾部而不是自己的服务端 tick 监听器里，是为了<b>不依赖模组加载顺序</b>：
 * 本体的监听器在模组构造期注册，我们后加载，顺序虽然通常正确，
 * 但挂在本体方法上才是确定性的。</p>
 *
 * <p>结算发生在所有电网都 tick 完之后，因此读到的是本拍刚算出来、且<b>已叠加本拍转移量</b>的
 * 账面余量；结果供下一拍使用（一拍延迟，见 {@link PowerInterconnectManager}）。</p>
 *
 * <p>V1.12.7 起这里<b>没有</b> HEAD 侧那个「开始收集」注入了：全模组只剩一套余量口径，
 * 结算直接读电网自己的账面值，不再需要每拍清空一张中间表——少一处状态就少一处漏改的可能。</p>
 */
@Mixin(PowerGridManager.class)
public abstract class PowerGridManagerTickMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void pullwires$settleInterconnect(CallbackInfo callback) {
        PowerInterconnectManager.settle();
    }
}
