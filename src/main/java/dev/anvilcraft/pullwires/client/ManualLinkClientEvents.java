package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.power.link.LinkBinding;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PowerLinkRule;
import dev.dubhe.anvilcraft.client.init.ModRenderTypes;
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

        // 主手优先，其次副手磁盘：「副手拿绑定磁盘、主手放杆」时同样要看到引导带。
        ItemStack carrier = LinkBinding.carrierOf(
            player, AnvilCraftPullWires.CONFIG.linking.allowDiskBinding
        );
        if (carrier == null) return;
        LinkBinding.Bound bound = LinkBinding.read(carrier);
        if (bound == null) return;
        // 主端头部的坐标只在主端所在维度里有意义。跨维度时主端不在本地世界，
        // 若照旧取坐标，会画出一条从本地某处指向下界坐标的虚假引导带。
        if (!ClientLinkVisibility.inLocalLevel(level, bound.master())) return;
        // 手持输电杆（开始链接）或手持磁盘（只绑定主端）都算。
        boolean holdingPole = PoleKind.isPoleItem(carrier);
        boolean holdingDisk = carrier.getItem() instanceof DiskItem;
        if (!holdingPole && !holdingDisk) return;

        Vec3 from = ManualLinkAim.masterHead(bound.master().pos());
        // 优先落在**视角指着的那根杆**的杆顶（磁盘那一路）；
        // 没指着杆时退回放置预览头部（手持杆准备放下的那一路）。
        Vec3 to = ManualLinkAim.aimedPoleHead(minecraft);
        if (to == null) to = ManualLinkAim.placementHead(minecraft);
        if (to == null) return;
        // 范围取自**主端**的种类：服务端的放置判定用的是主端的范围（含「跨维度杆无距离限制」），
        // 这里必须同源，否则会出现「看着在范围内，放下却被拒绝」。
        // 距离也必须与服务端同一个标量（长边长度），否则引导带会在还能连的位置提前消失。
        if (!PoleKind.unlimitedConnectDistance(bound.kind())
            && PowerLinkRule.boxDistance(from.x, from.y, from.z, to.x, to.y, to.z)
               > PoleKind.manualConnectRange(bound.kind())) return;

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
     * <p>它把 {@code addParticle} 的后三个参数当作<b>相对于终点的位移</b>（不是速度），每 tick 按下式定位：</p>
     * <pre>
     * f  = age / lifetime
     * f1 = -f + 2f²
     * f2 = 1 - f1            // 1 → 1.125 → 0
     * x  = xStart + dx * f2
     * y  = yStart + dy * f2 + (1 - f)
     * z  = zStart + dz * f2
     * </pre>
     * <p><b>关键在于谁是终点</b>：{@code f2} 从 1 缓动到 0，所以粒子是<b>从「位置 + 位移」出发、
     * 收束到「位置」</b>——传进去的坐标是<b>终点</b>，位移表示它离终点有多远。</p>
     * <p>（第一版把方向看反了：拿随机点当位置、杆头当位移，于是粒子从杆头飞向外面。）</p>
     * <p>因此「在随机位置出现、飞向杆头」= 位置传<b>杆头</b>、位移传<b>起点 − 杆头</b>。</p>
     * <p>{@code y} 那一项多出来的 {@code (1 - f)} 是原版的抬起量（起点比直线高约 1 格），
     * 这里从位移里预先减掉 1，使得粒子的实际出现位置正好是选定的随机点。</p>
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
                // 位置 = 终点 = 杆头
                headX, headY, headZ,
                // 位移 = 起点 − 杆头（y 再减掉原版那 1 格抬起）
                startX - headX, startY - headY - 1.0, startZ - headZ
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
    /**
     * 光晕那一趟：只在本体的光晕渲染阶段被调用（见 {@code PowerGridSupportMixin}）。
     *
     * <p>顶点用 {@code ModRenderTypes.LINE_BLOOM} 写进**已经绑定好的**光晕帧缓冲，
     * 这样才能与本体的输电杆线一样发光。普通管线那趟仍然照常画，所以这一趟失败也不影响可见性。</p>
     */
    public static void renderBloomedLines(
        PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera
    ) {
        if (!AnvilCraftPullWiresClient.CONFIG.graphics.renderManualLinkLines) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        List<LinkDefinition> links = ManualLinkClientState.links();
        if (links.isEmpty()) return;
        VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.LINE_BLOOM);
        for (LinkDefinition definition : links) {
            if (!ClientLinkVisibility.drawsLine(level, definition)) continue;
            Vec3 from = Vec3.atCenterOf(definition.master().pos());
            Vec3 to = Vec3.atCenterOf(definition.slave().pos());
            new Line(from, to, (float) from.distanceTo(to))
                .render(pose, consumer, camera, colorFor(ManualLinkClientState.stateOf(definition.id())));
        }
        buffers.endBatch();
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
        // 这一趟**永远用普通管线**：它负责「线看得见」。
        // 光晕是**另一趟**，挂在 PowerGridSupport#renderEnhancedTransmitterLine 的 TAIL 上
        // （见 PowerGridSupportMixin）——因为 LINE_BLOOM 只有在那个渲染阶段、那个帧缓冲下才有意义。
        // 之前我把这两趟混成一趟、按配置替换 RenderType，结果顶点写进了未绑定的光晕缓冲、线整条消失。
        VertexConsumer consumer = buffers.getBuffer(RenderType.LINES);
        for (LinkDefinition definition : links) {
            // 跨维度链路与「端点涉及跨维度杆」的链路都不画线，判定见 ClientLinkVisibility。
            if (!ClientLinkVisibility.drawsLine(level, definition)) continue;
            // 端点用方块中心：**与本体自己的电力线同高**。之前我把它抬到杆顶，
            // 结果本模组的线比本体的高半格——同一条走廊上两种线一高一低，很难看。
            // 「杆顶」只是粒子束的锚点，不是连接线的。
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
