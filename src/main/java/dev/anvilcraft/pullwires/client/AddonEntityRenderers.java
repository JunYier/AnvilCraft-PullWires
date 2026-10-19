package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.init.AddonBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 客户端渲染器注册。
 *
 * <p>目前只有一处：跨维度杆顶段的 {@code core} 需要走末影传送门着色器，
 * 而方块模型系统表达不了那种「屏幕空间投影 + 多层叠加」的效果，
 * 所以必须由方块实体渲染器自己发顶点（见 {@link CrossDimensionalPoleCoreRenderer}）。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID, value = Dist.CLIENT)
public final class AddonEntityRenderers {
    private AddonEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            AddonBlockEntities.CROSS_DIMENSIONAL_POLE.get(),
            CrossDimensionalPoleCoreRenderer::create
        );
    }
}