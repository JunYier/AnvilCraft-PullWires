package dev.anvilcraft.pullwires.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.pullwires.client.ManualLinkClientEvents;
import dev.dubhe.anvilcraft.client.support.PowerGridSupport;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让本模组的手动连接线也走本体的<b>光晕</b>渲染阶段。
 *
 * <h2>为什么要挂在别人的方法上</h2>
 * <p>光晕不是「换个 RenderType 就行」：本体的 {@code PowerGridSupport#renderEnhancedTransmitterLine}
 * 会先把 {@code ModRenderTargets.getBloomTarget()} 清空、从主帧缓冲拷贝深度，然后才用
 * {@code ModRenderTypes.LINE_BLOOM} 写顶点。也就是说 <b>{@code LINE_BLOOM} 只在那个阶段、
 * 那个帧缓冲下才有意义</b>。</p>
 *
 * <p>我一开始图省事，在自己的 {@code AFTER_BLOCK_ENTITIES} 渲染里按配置把 RenderType 从
 * {@code LINES} 换成 {@code LINE_BLOOM}——结果顶点被写进了**没有绑定的光晕缓冲**，
 * 线整条消失。教训：渲染类型不能脱离它所属的渲染阶段单独替换。</p>
 *
 * <h2>这个注入点为什么选 TAIL</h2>
 * <p>该方法在「增强渲染不可用 / 光晕开关关闭」时会<b>提前 return</b>，而 {@code At("TAIL")}
 * 只匹配最后一个 RETURN，因此我们的注入<b>只在本体真的画了光晕线时</b>才执行——
 * 「要不要发光」这个决定完全由本体做，我们不自带任何开关，天然与本体配置一致。</p>
 *
 * <p>{@code require = 0}：本体改这个方法时静默退化为「没有光晕」，而不会让游戏起不来。
 * 普通管线那一趟始终会画出线，所以可见性不依赖这里。</p>
 */
@Mixin(PowerGridSupport.class)
public abstract class PowerGridSupportMixin {
    @Inject(method = "renderEnhancedTransmitterLine", at = @At("TAIL"), require = 0)
    private static void pullwires$renderBloomedLinkLines(
        PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camera, CallbackInfo callback
    ) {
        ManualLinkClientEvents.renderBloomedLines(poseStack, bufferSource, camera);
    }
}
