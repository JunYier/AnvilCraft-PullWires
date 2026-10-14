package dev.anvilcraft.pullwires.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;

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
    private static final int PARTICLE_SPACING = 4;

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
        return Component.empty()
            .append(marker)
            .append(Component.literal(" " + shortPos(definition.master().pos())
                                      + " -> " + shortPos(definition.slave().pos()))
                .withStyle(ChatFormatting.GRAY));
    }

    private static String shortPos(net.minecraft.core.BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 每 particleBeamInterval tick 重新生成一束粒子，只画同维度链路。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!AnvilCraftPullWiresClient.CONFIG.ui.connectionListAlwaysOnDisplay) return;
        if (!AnvilCraftPullWiresClient.CONFIG.effects.linkParticleBeam) return;

        int interval = Mth.clamp(AnvilCraftPullWiresClient.CONFIG.ui.particleBeamInterval, 20, 7200);
        if (minecraft.level.getGameTime() % interval != 0) return;

        LocalPlayer player = minecraft.player;
        for (LinkDefinition definition : ManualLinkClientState.links()) {
            if (!definition.master().dimension().equals(minecraft.level.dimension())) continue;
            Vec3 from = Vec3.atCenterOf(definition.master().pos()).add(0, 1, 0);
            Vec3 to = Vec3.atCenterOf(definition.slave().pos()).add(0, 1, 0);
            int steps = Mth.clamp((int) Math.ceil(from.distanceTo(to) / PARTICLE_SPACING), 2, 128);
            for (int i = 1; i < steps; i++) {
                double t = (double) i / steps;
                double x = Mth.lerp(t, from.x, to.x);
                double y = Mth.lerp(t, from.y, to.y);
                double z = Mth.lerp(t, from.z, to.z);
                minecraft.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }
}