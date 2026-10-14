package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.power.link.LinkBinding;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.dubhe.anvilcraft.client.renderer.Line;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /** 跨维度杆顶端的传送门粒子节拍：每 4 tick 一次、每次 2–3 个（需求 §5.2）。 */
    private static final int PORTAL_PARTICLE_INTERVAL = 4;

    /** 粒子出现区域：以杆头顶端为中心的水平半径（内 / 外）与竖向范围。 */
    private static final double PORTAL_RADIUS_MIN = 1.2;
    private static final double PORTAL_RADIUS_MAX = 3.0;
    private static final double PORTAL_Y_MIN = -0.5;
    private static final double PORTAL_Y_MAX = 2.0;


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

        // 传送门粒子与「手上拿着什么」无关，必须在下面的提前返回之前跑：
        // 跨维度杆连上之后，玩家手上多半什么都没拿。
        spawnCrossDimensionPortals(level);

        ItemStack main = player.getMainHandItem();
        LinkBinding.Bound bound = LinkBinding.read(main);
        if (bound == null) return;
        // 主板头部的坐标只在主端所在维度里有意义。跨维度时主端不在本地世界，
        // 若照旧取坐标，会画出一条从本地某处指向下界坐标的虚假引导带。
        if (!ClientLinkVisibility.inLocalLevel(level, bound.master())) return;
        // 手持输电杆（开始链接）或手持磁盘（只绑定主端）都算。
        boolean holdingPole = PoleKind.isPoleItem(main);
        boolean holdingDisk = main.getItem() instanceof DiskItem;
        if (!holdingPole && !holdingDisk) return;

        Vec3 from = ManualLinkAim.masterHead(bound.master().pos());
        Vec3 to = ManualLinkAim.placementHead(minecraft);
        if (to == null) return;
        // 范围取自**主端**的种类：服务端的放置判定用的是主端的范围（含「跨维度杆无距离限制」），
        // 这里必须同源，否则会出现「看着在范围内，放下却被拒绝」。
        if (!PoleKind.unlimitedConnectDistance(bound.kind())
            && from.distanceTo(to) > PoleKind.manualConnectRange(bound.kind())) return;

        spawnSparks(level, from, to);
    }

    /**
     * 跨维度杆的连接态表现：顶端持续散发向上漂移的传送门粒子。
     *
     * <p>一根杆可能同时挂在多条链路上（一主多从），因此这里按<b>坐标去重</b>——
     * 否则挂 5 条链路就会变成 5 倍的粒子量。</p>
     */
    private static void spawnCrossDimensionPortals(ClientLevel level) {
        if (!AnvilCraftPullWiresClient.CONFIG.effects.renderCrossDimensionPortalParticles) return;
        if (level.getGameTime() % PORTAL_PARTICLE_INTERVAL != 0) return;
        if (ManualLinkClientState.links().isEmpty()) return;

        Set<BlockPos> emitted = new HashSet<>();
        for (LinkDefinition definition : ManualLinkClientState.links()) {
            LinkState state = ManualLinkClientState.stateOf(definition.id());
            for (PoleKey end : List.of(definition.master(), definition.slave())) {
                if (!ClientLinkVisibility.emitsPortalParticles(level, end, state)) continue;
                if (emitted.add(end.pos())) spawnPortalBurst(level, end.pos());
            }
        }
    }

    /**
     * 一束传送门粒子：在杆头周围的随机位置出现，然后沿直线飞向杆头。
     *
     * <h2>运动模型直接借原版 {@code PortalParticle}</h2>
     * <p>它把 {@code addParticle} 的后三个参数当作<b>整个生命周期内的总位移</b>（不是速度），
     * 每 tick 按下式定位：</p>
     * <pre>
     * f  = age / lifetime
     * f1 = -f + 2f²          // f2 = 1 - f1：0 → 1 → 1.125 → 1 → 0
     * x  = xStart + dx * f2
     * y  = yStart + dy * f2 + (1 - f)   // 一点点抬起，位置略微上凸
     * z  = zStart + dz * f2
     * </pre>
     * <p>所以只要把「杆头 − 起点」当作总位移传进去，粒子就会在生命周期结束时<b>精确落在杆头上</b>，
     * 中途自然收敛，不需要每 tick 干预、也不需要自己算速度衰减。这与下界传送门的粒子是同一套代码，
     * 因此观感一致——「在一定区域内出现，然后被吸向杆头」。</p>
     */
    private static void spawnPortalBurst(ClientLevel level, BlockPos top) {
        double headX = top.getX() + 0.5;
        double headY = top.getY() + 1.0;
        double headZ = top.getZ() + 0.5;
        int count = 2 + level.getRandom().nextInt(2);
        for (int i = 0; i < count; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = PORTAL_RADIUS_MIN
                + level.getRandom().nextDouble() * (PORTAL_RADIUS_MAX - PORTAL_RADIUS_MIN);
            double startX = headX + Math.cos(angle) * radius;
            double startY = headY + PORTAL_Y_MIN + level.getRandom().nextDouble() * (PORTAL_Y_MAX - PORTAL_Y_MIN);
            double startZ = headZ + Math.sin(angle) * radius;
            level.addParticle(
                ParticleTypes.PORTAL,
                startX, startY, startZ,
                headX - startX, headY - startY, headZ - startZ
            );
        }
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
        ClientLevel level = minecraft.level;
        List<LinkDefinition> links = ManualLinkClientState.links();
        if (links.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer consumer = buffers.getBuffer(RenderType.LINES);
        for (LinkDefinition definition : links) {
            // 跨维度链路与「端点涉及跨维度杆」的链路都不画线，判定见 ClientLinkVisibility。
            if (!ClientLinkVisibility.drawsLine(level, definition)) continue;
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
