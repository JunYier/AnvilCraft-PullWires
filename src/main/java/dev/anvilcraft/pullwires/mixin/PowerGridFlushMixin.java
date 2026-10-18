package dev.anvilcraft.pullwires.mixin;

import dev.anvilcraft.pullwires.power.link.PowerInterconnectManager;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把链路互联的结算结果接回电网的<b>二元账本</b>。
 *
 * <h2>方案：转移记进 generate / consume（V1.12.3）</h2>
 * <p>本体的账本只有 {@code generate} / {@code consume} 两个桶，而跨网转移在物理上就是
 * <b>一端的发电、另一端的耗电</b>：从端确实"进账"了，主端确实"出账"了。
 * 所以这里把它记进这两个桶，而不是在出口方法上另叠一个第三桶。</p>
 *
 * <p>这样做的收益是<b>全链路自动一致</b>：</p>
 * <ul>
 *   <li>{@code getRemaining()} / {@code isWorking()} 自动含转移；</li>
 *   <li>{@code flushState()} 同步的 {@code OVERLOAD} 方块属性自动正确；</li>
 *   <li>{@code tick()} 里储电的 {@code insert}/{@code extract} 分配自动正确
 *       （那段代码直接读两个字段的局部变量，任何"出口叠加"方案都会与它不一致）；</li>
 *   <li>客户端同步包与 tooltip 自动一致，不需要再补第三个数的通道。</li>
 * </ul>
 * <p>代价是本体显示的「总发电量 / 总耗电量」包含转移。但本体的 tooltip 本来就是
 * <b>两层</b>结构：逐台机器的「发电统计 / 耗电统计」不含转移（那份列表来自元件集合），
 * 两个总数含转移——差额正好就是转移量，再配上本模组自己的「电网进口 / 出口」两行即可读懂。</p>
 *
 * <h2>只有这一个注入点</h2>
 * <p>V1.12.7 起不再在 TAIL 之前「先记录自有余额」——全模组只剩账面余量一套口径
 * （理由见 {@link PowerInterconnectManager}），因此这里只做一件事：注入。
 * 少了「先记录、后注入」这个顺序约定，就少了最容易漏改的一类 bug。</p>
 *
 * <p>注入一律 {@code require = 0}：本体改签名时静默退化为「转移不影响电网」，
 * 而不是让游戏起不来。</p>
 */
@Mixin(PowerGrid.class)
public abstract class PowerGridFlushMixin {
    @Shadow
    private int generate;

    @Shadow
    private int consume;

    @Inject(method = "flush", at = @At("TAIL"), require = 0)
    private void pullwires$bookTransfer(CallbackInfoReturnable<Boolean> callback) {
        PowerGrid grid = (PowerGrid) (Object) this;

        // 把跨网转移记进二元账：净进口抬高发电侧，净出口抬高耗电侧。
        // 下一个电网 tick 的结算就能读到这个账面值，中继链因此可以逐跳导通。
        int delta = PowerInterconnectManager.deltaFor(grid);
        if (delta > 0) {
            this.generate += delta;
        } else if (delta < 0) {
            this.consume -= delta;
        }
    }
}
