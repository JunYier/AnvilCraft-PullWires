package dev.anvilcraft.pullwires.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.pullwires.block.entity.CrossDimensionalPoleBlockEntity;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 跨维度杆顶段里那颗 {@code core} 的渲染器：用<b>末影传送门的投影着色器</b>画它。
 *
 * <h2>为什么不用模型</h2>
 * <p>{@code core} 按要求不参与普通方块模型渲染（放置模型里已经把它移除，见
 * {@code cross_dimensional_pole_head.json}），因为它要的不是一张贴图，而是原版
 * {@link RenderType#endPortal()} —— 那套管线<b>顶点格式只有 POSITION</b>、贴图坐标由着色器按
 * 屏幕空间投影算出（{@code textureProj}），所以它看起来像一扇通往别处的窗口，而不像方块表面。
 * 方块模型系统表达不了这种效果，只能由方块实体渲染器自己发顶点。</p>
 *
 * <h2>几何来源</h2>
 * <p>取自 {@code cross_dimensional_pole_head.json} 里名为 {@code core} 的 cube：
 * {@code from [5,9,5] to [11,15,11]}（16 单位格内），换算成方块空间就是各分量除以 16。</p>
 *
 * <h2>绕序</h2>
 * <p>{@code endPortal} 是带背面剔除的不透明管线，但它的渲染状态不在我们手里。为了不因为绕序写反而
 * 整块消失，这里每个面<b>正反各发一次</b>：错误的那一半会被剔除掉，不会产生任何可见副作用，
 * 也不会与另一半 z-fighting（顶点完全重合，只是绕序相反）。</p>
 */
public class CrossDimensionalPoleCoreRenderer implements BlockEntityRenderer<CrossDimensionalPoleBlockEntity> {
    /** {@code core} 在 16 单位格内的下界。 */
    private static final float MIN_X = 5.0F / 16.0F;
    private static final float MIN_Y = 9.0F / 16.0F;
    private static final float MIN_Z = 5.0F / 16.0F;
    /** {@code core} 在 16 单位格内的上界。 */
    private static final float MAX_X = 11.0F / 16.0F;
    private static final float MAX_Y = 15.0F / 16.0F;
    private static final float MAX_Z = 11.0F / 16.0F;

    public CrossDimensionalPoleCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        CrossDimensionalPoleBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();
        // 只有顶段拥有这颗 core。
        if (state.getValue(RemoteTransmissionPoleBlock.HALF) != Vertical4PartHalf.TOP) return;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.endPortal());
        PoseStack.Pose pose = poseStack.last();
        // 西 / 东
        face(consumer, pose, MIN_X, MIN_Y, MIN_Z, MIN_X, MIN_Y, MAX_Z, MIN_X, MAX_Y, MAX_Z, MIN_X, MAX_Y, MIN_Z);
        face(consumer, pose, MAX_X, MIN_Y, MIN_Z, MAX_X, MAX_Y, MIN_Z, MAX_X, MAX_Y, MAX_Z, MAX_X, MIN_Y, MAX_Z);
        // 下 / 上
        face(consumer, pose, MIN_X, MIN_Y, MIN_Z, MAX_X, MIN_Y, MIN_Z, MAX_X, MIN_Y, MAX_Z, MIN_X, MIN_Y, MAX_Z);
        face(consumer, pose, MIN_X, MAX_Y, MIN_Z, MIN_X, MAX_Y, MAX_Z, MAX_X, MAX_Y, MAX_Z, MAX_X, MAX_Y, MIN_Z);
        // 北 / 南
        face(consumer, pose, MIN_X, MIN_Y, MIN_Z, MIN_X, MAX_Y, MIN_Z, MAX_X, MAX_Y, MIN_Z, MAX_X, MIN_Y, MIN_Z);
        face(consumer, pose, MIN_X, MIN_Y, MAX_Z, MAX_X, MIN_Y, MAX_Z, MAX_X, MAX_Y, MAX_Z, MIN_X, MAX_Y, MAX_Z);
    }

    /** 发一个四边形，正反两种绕序各一次（错误的那一半会被背面剔除）。 */
    private static void face(
        VertexConsumer consumer, PoseStack.Pose pose,
        float ax, float ay, float az,
        float bx, float by, float bz,
        float cx, float cy, float cz,
        float dx, float dy, float dz
    ) {
        vertex(consumer, pose, ax, ay, az);
        vertex(consumer, pose, bx, by, bz);
        vertex(consumer, pose, cx, cy, cz);
        vertex(consumer, pose, dx, dy, dz);
        vertex(consumer, pose, dx, dy, dz);
        vertex(consumer, pose, cx, cy, cz);
        vertex(consumer, pose, bx, by, bz);
        vertex(consumer, pose, ax, ay, az);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z) {
        // endPortal 的顶点格式只有 POSITION，因此不能也不要写 uv/颜色/法线。
        consumer.addVertex(pose, x, y, z);
    }

    /** 渲染器注册入口用的工厂。 */
    public static CrossDimensionalPoleCoreRenderer create(BlockEntityRendererProvider.Context context) {
        return new CrossDimensionalPoleCoreRenderer(context);
    }

}
