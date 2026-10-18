package dev.anvilcraft.pullwires.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.SyncLinkStatsPacket;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.dubhe.anvilcraft.util.UnitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;
import java.util.UUID;

/**
 * 常驻连接列表与粒子束可视化（由 /wires display 控制）。
 *
 * <p>列表常驻并实时更新——数据来自每拍同步的 {@code SyncLinkStatsPacket}，
 * 因此新建 / 删除 / 状态变化都会自动反映，不需要额外刷新机制。</p>
 *
 * <p>粒子束<b>每 {@code particleBeamInterval} tick 重新生成一束</b>，而不是持续发射：
 * 长距离链路每 tick 发几十个粒子的开销与视觉噪声都不可接受。仅同维度链路会生成。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID, value = Dist.CLIENT)
public final class ConnectionListHud {
    private static final int LINE_HEIGHT = 10;

    /** 粒子线从主端飞到从端所需的 tick 数。 */
    private static final int TRAVEL_TICKS = 40;

    /** 粒子线在世界里的长度（格）：太短像点，太长就不像「一段线」。 */
    private static final double DASH_LENGTH = 2.5;

    /** 一段线采样几个粒子。 */
    private static final int DASH_SAMPLES = 8;

    private ConnectionListHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CHAT,
            AnvilCraftPullWires.of("connection_list"),
            ConnectionListHud::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) return;
        if (minecraft.screen != null) return;
        if (!AnvilCraftPullWiresClient.CONFIG.ui.connectionListAlwaysOnDisplay) return;

        List<LinkDefinition> links = ManualLinkClientState.links();
        if (links.isEmpty()) return;

        int maxRows = AnvilCraftPullWiresClient.CONFIG.ui.connectionListMaxRows;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(
            AnvilCraftPullWiresClient.CONFIG.ui.connectionListX,
            AnvilCraftPullWiresClient.CONFIG.ui.connectionListY,
            0
        );
        float scale = AnvilCraftPullWiresClient.CONFIG.ui.connectionListScale;
        pose.scale(scale, scale, 1.0F);

        graphics.drawString(minecraft.font, header(links.size()), 0, 0, 0xFFFFFFFF, true);
        int shown = Math.min(links.size(), maxRows);
        for (int i = 0; i < shown; i++) {
            LinkDefinition definition = links.get(i);
            LinkState state = ManualLinkClientState.stateOf(definition.id());
            graphics.drawString(
                minecraft.font, row(definition, state), 0, LINE_HEIGHT * (i + 1), 0xFFFFFFFF, true
            );
        }
        if (links.size() > shown) {
            graphics.drawString(
                minecraft.font,
                Component.translatable("hud.anvilcraft_pullwires.connection_list.more", links.size() - shown)
                    .withStyle(ChatFormatting.DARK_GRAY),
                0, LINE_HEIGHT * (shown + 1), 0xFFFFFFFF, true
            );
        }
        pose.popPose();
    }

    private static Component header(int count) {
        return Component.translatable("hud.anvilcraft_pullwires.connection_list.header", count)
            .withStyle(ChatFormatting.GOLD);
    }

    private static Component row(LinkDefinition definition, LinkState state) {
        MutableComponent marker = Component.literal("\u25CF")
            .withStyle(switch (state) {
                case ACTIVE -> ChatFormatting.GREEN;
                case INACTIVE -> ChatFormatting.DARK_GRAY;
                case SUSPENDED -> ChatFormatting.RED;
                case PENDING -> ChatFormatting.YELLOW;
            });
        MutableComponent row = Component.empty()
            .append(marker)
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(endpoint(definition.master()))
            .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
            .append(endpoint(definition.slave()));
        return row.append(transfer(definition.id()));
    }

    /**
     * 一个端点：{@code 维度 坐标}。
     *
     * <p>跨维度链路的两端隔着世界，只写坐标根本分不清哪个在哪——所以维度必须显示，
     * 而且要用玩家当前语言的名字。维度名走标准的 {@code dimension.<命名空间>.<路径>} 键，
     * 本体与藿香都已经为原版三个维度提供了翻译；模组维度若缺翻译，
     * 用 {@code translatableWithFallback} 退化成它的路径名，而不是把原始键名丢给玩家。</p>
     */
    private static Component endpoint(dev.anvilcraft.pullwires.power.link.PoleKey key) {
        ResourceLocation dimension = key.dimension().location();
        return Component.empty()
            .append(Component.translatableWithFallback(
                "dimension." + dimension.toLanguageKey(), dimension.getPath()
            ).withStyle(ChatFormatting.DARK_AQUA))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(shortPos(key.pos())).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 这一条链路本拍的转移量。
     *
     * <p>两端数值相同时只显示一个；跨维度链路因损耗而不等，显示成
     * {@code 送出 → 送达}，让损耗直接可见——这正是「每对主从」比「按杆汇总」多出来的信息。</p>
     *
     * <p>尚未同步到、或者本拍没有成交（都是 0）时不追加任何东西，
     * 免得一整屏的「0 kW」把列表淹没。</p>
     */
    private static Component transfer(UUID id) {
        SyncLinkStatsPacket.StateEntry entry = ManualLinkClientState.amountOf(id);
        if (entry == null) return Component.empty();
        int sent = entry.sent();
        int received = entry.received();
        if (sent <= 0 && received <= 0) return Component.empty();

        if (sent == received) {
            return Component.translatable(
                "hud.anvilcraft_pullwires.connection_list.transfer",
                Component.literal(electricity(sent)).withStyle(ChatFormatting.AQUA)
            ).withStyle(ChatFormatting.DARK_GRAY);
        }
        // 跨维度：两端不等，把损耗画进箭头里。
        return Component.translatable(
            "hud.anvilcraft_pullwires.connection_list.transfer_lossy",
            Component.literal(electricity(sent)).withStyle(ChatFormatting.AQUA),
            Component.literal(electricity(received)).withStyle(ChatFormatting.BLUE)
        ).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** 与 tooltip 同一个口径：按住 shift 时取原始数值，否则允许换算到 MW。 */
    private static String electricity(int amount) {
        return UnitUtil.electricityUnit(amount, Screen.hasShiftDown(), false);
    }

    private static String shortPos(net.minecraft.core.BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /**
     * 粒子线：每 {@code particleBeamInterval}（默认 100）tick 从主端出发，
     * 在 {@link #TRAVEL_TICKS} tick 内<b>匀速直线</b>飞到从端。
     *
     * <p>不是「整条线一起亮」，而是一段固定长度的粒子往从端跑——这样一眼能看出<b>流向</b>，
     * 而不是只看到两点之间有东西。相位的算法是纯函数（{@code gameTime + 链路自己的偏移} 取模周期），
     * 不保存任何状态：退出重进、切维度都不会残留一半的动画。</p>
     *
     * <p>每条链路用自己的偏移，否则所有链路会整齐划一地一起脉冲，看着像 bug。</p>
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!AnvilCraftPullWiresClient.CONFIG.ui.connectionListAlwaysOnDisplay) return;
        if (!AnvilCraftPullWiresClient.CONFIG.effects.linkParticleBeam) return;

        ClientLevel level = minecraft.level;
        // 周期必须比飞行时间长，否则上一段还没飞到下一段就出发了。
        int period = Math.max(
            TRAVEL_TICKS + 20,
            Mth.clamp(AnvilCraftPullWiresClient.CONFIG.ui.particleBeamInterval, 20, 7200)
        );
        long gameTime = level.getGameTime();

        for (LinkDefinition definition : ManualLinkClientState.links()) {
            // 与电力线渲染同一份判定：跨维度链路不画，端点涉及跨维度杆的也不画。
            if (!ClientLinkVisibility.drawsLine(level, definition)) continue;

            int phase = (int) Math.floorMod(
                gameTime + Math.floorMod(definition.id().getLeastSignificantBits(), period), (long) period
            );
            if (phase >= TRAVEL_TICKS) continue;

            spawnDash(
                level, Vec3.atCenterOf(definition.master().pos()).add(0, 1, 0),
                Vec3.atCenterOf(definition.slave().pos()).add(0, 1, 0),
                (double) phase / TRAVEL_TICKS
            );
        }
    }

    /**
     * 在 {@code t}（0→1，主端→从端）处画一小段粒子。
     *
     * <p>段的长度是<b>世界里的固定格数</b>而不是固定比例：链路很长时，按比例会拉成一条长带子，
     * 看起来就不像「一段线」了。</p>
     */
    private static void spawnDash(ClientLevel level, Vec3 from, Vec3 to, double t) {
        double total = from.distanceTo(to);
        if (total <= 0.01) return;
        double head = Mth.clamp(t, 0.0, 1.0);
        double tail = Math.max(0.0, head - Math.min(1.0, DASH_LENGTH / total));
        if (head <= tail) return;
        for (int i = 0; i <= DASH_SAMPLES; i++) {
            Vec3 p = from.lerp(to, tail + (head - tail) * i / DASH_SAMPLES);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0.0, 0.0, 0.0);
        }
    }
}