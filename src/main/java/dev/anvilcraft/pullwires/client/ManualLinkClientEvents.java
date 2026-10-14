package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.init.AddonBlocks;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.power.link.LinkBinding;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.client.AnvilCraftClient;
import dev.dubhe.anvilcraft.client.renderer.Line;
import dev.dubhe.anvilcraft.constant.Constant;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.item.DiskItem;
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
import net.minecraft.world.item.ItemStack;
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
        if (player == null || level == null) return;
        PoleKey master = LinkBinding.read(player.getMainHandItem());
        if (master == null) return;
        // 手持输电杆（开始链接）或手持磁盘（只绑定主端）都算。
        ItemStack main = player.getMainHandItem();
        boolean holdingPole = PoleKind.isPoleItem(main);
        boolean holdingDisk = player.getMainHandItem().getItem() instanceof DiskItem;
        if (!holdingPole && !holdingDisk) return;

        Vec3 from = ManualLinkAim.masterHead(master.pos());
        Vec3 to = ManualLinkAim.placementHead(minecraft);
        if (to == null) return;
        if (from.distanceTo(to) > PoleKind.manualConnectRangeOfItem(player.getMainHandItem())) return;

        spawnSparks(level, from, to);
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
    /** 链路状态 → 线色。默认值与本体同色，因此不配置时的观感与之前一致。 */
    private static int colorFor(LinkState state) {
        int rgb = switch (state) {
            case ACTIVE -> AnvilCraftPullWiresClient.CONFIG.graphics.colorActive;
            case INACTIVE -> AnvilCraftPullWiresClient.CONFIG.graphics.colorInactive;
            case SUSPENDED -> AnvilCraftPullWiresClient.CONFIG.graphics.colorSuspended;
            case PENDING -> AnvilCraftPullWiresClient.CONFIG.graphics.colorPending;
        };
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        // 本附属自己的开关，与本体 renderPowerTransmitterLines 解耦：本体线色不受影响。
        if (!AnvilCraftPullWiresClient.CONFIG.graphics.renderManualLinkLines) return;

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
                .render(pose, consumer, camera, colorFor(ManualLinkClientState.stateOf(definition.id())));
        }
        buffers.endBatch();
    }

    /** 客户端侧状态是静态的，退出服务器时必须清掉，否则会把上一局的链路带到下一个世界。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ManualLinkClientState.clear();
    }
}
