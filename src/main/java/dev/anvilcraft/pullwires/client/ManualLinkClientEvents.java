package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.CancelManualLinkPacket;
import dev.anvilcraft.pullwires.network.StartManualLinkPacket;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.client.AnvilCraftClient;
import dev.dubhe.anvilcraft.client.renderer.Line;
import dev.dubhe.anvilcraft.constant.Constant;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 客户端的手动连接表现与入口。
 *
 * <p>热键是纯客户端状态，服务端看不到，所以「按住热键右键」这个动作必须在客户端拦截并上报。
 * 这里只负责<b>发起</b>与<b>取消</b>；坐标、距离、手持物全部由服务端重新校验。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID, value = Dist.CLIENT)
public class ManualLinkClientEvents {
    /** 粒子束的采样间距与上限：线越长步长越大，避免刷出成百上千个粒子。 */
    private static final double SPARK_SPACING = 0.75;
    private static final int MAX_SPARKS = 64;

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        if (!AddonKeyMappings.MANUAL_LINK.get().isDown()) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        if (!player.getMainHandItem().is(ModBlocks.TRANSMISSION_POLE.get().asItem())) return;

        HitResult hit = minecraft.hitResult;
        // 注意：什么都没打中时原版返回的是 BlockHitResult（Type.MISS），不是 null。
        // 只按 instanceof 判断会把「右键空气」误当成「命中了一个非杆方块」，取消包就永远发不出去。
        if (hit instanceof BlockHitResult blockHit
            && blockHit.getType() == HitResult.Type.BLOCK
            && PoleResolver.isPoleTop(minecraft.level, blockHit.getBlockPos())) {
            PacketDistributor.sendToServer(new StartManualLinkPacket(blockHit.getBlockPos()));
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            // 右键空气：显式取消待连接状态。
            PacketDistributor.sendToServer(new CancelManualLinkPacket());
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        // 瞄准的是别的方块：交回原版处理，不要吞掉玩家的正常交互。
    }

    /**
     * 待连接期间把避雷针粒子从主端头部引到「方块放置预览轮廓」的头部。
     *
     * <p>超出手动连接范围时直接暂停生成——按需求只保留白色避雷针粒子，不做红色告警态。</p>
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        BlockPos master = ManualLinkClientState.pendingMaster();
        if (master == null || player == null || level == null) return;
        if (!player.getMainHandItem().is(ModBlocks.TRANSMISSION_POLE.get().asItem())) return;

        Vec3 from = headOf(master);
        Vec3 to = placementHead(minecraft);
        if (to == null) return;
        if (from.distanceTo(to) > AnvilCraftPullWires.CONFIG.powerLink.manualConnectRange) return;

        spawnSparks(level, from, to);
    }

    private static Vec3 headOf(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0.0, 1.0, 0.0);
    }

    /**
     * 方块放置预览的头部。
     *
     * <p>用当前帧的 {@code hitResult} 推算出「这一下会放在哪一格」，与本体画碰撞箱勾勒用的是同一个依据。
     * 没瞄准任何方块时没有预览轮廓，自然也不生成粒子。</p>
     */
    private static @Nullable Vec3 placementHead(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos placement = hit.getBlockPos().relative(hit.getDirection());
        return headOf(placement);
    }

    private static void spawnSparks(ClientLevel level, Vec3 from, Vec3 to) {
        int steps = Mth.clamp((int) Math.ceil(from.distanceTo(to) / SPARK_SPACING), 2, MAX_SPARKS);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            level.addParticle(
                ParticleTypes.ELECTRIC_SPARK,
                Mth.lerp(t, from.x, to.x),
                Mth.lerp(t, from.y, to.y),
                Mth.lerp(t, from.z, to.z),
                0.0, 0.0, 0.0
            );
        }
    }

    /**
     * 绘制手动连接的电力线。
     *
     * <p>不能借用本体的 {@code SimplePowerGrid#powerTransmitterLines}：那是<b>按电网</b>算的，
     * 而手动连接恰恰用于连接两座<b>不同</b>的电网，两端各自都不在同一座电网的成员表里，
     * 挂在任一网格上都画不出来。这里直接按服务端同步下来的链路列表画。</p>
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        if (!AnvilCraftClient.CONFIG.graphics.renderPowerTransmitterLines) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        String dimension = minecraft.level.dimension().location().toString();
        List<LinkDefinition> links = ManualLinkClientState.links();
        if (links.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer consumer = buffers.getBuffer(RenderType.LINES);
        for (LinkDefinition definition : links) {
            if (!definition.master().dimension().location().toString().equals(dimension)) continue;
            Vec3 from = Vec3.atCenterOf(definition.master().pos());
            Vec3 to = Vec3.atCenterOf(definition.slave().pos());
            new Line(from, to, (float) from.distanceTo(to))
                .render(pose, consumer, camera, Constant.TRANSMITTER_LINE_COLOR);
        }
        buffers.endBatch();
    }

    /** 客户端侧状态是静态的，退出服务器时必须清掉，否则会把上一局的链路带到下一个世界。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ManualLinkClientState.clear();
    }
}
