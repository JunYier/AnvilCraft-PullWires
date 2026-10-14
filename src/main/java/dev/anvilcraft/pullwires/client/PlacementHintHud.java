package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 待放置输电杆落在 autoR 内时的绿色 >< 指示。
 *
 * <p>参照本体在经验条两端画的蓝色 &lt;&gt;（{@code PowerGridHUD}）：同样是 7 行逐行内缩的
 * {@code graphics.fill} 尖角，但方向相反以区分「已入网」与「可入网」，并画在本体那对的外侧。</p>
 */
public final class PlacementHintHud {
    private static final int ROWS = 7;
    private static final int EXPERIENCE_BAR_WIDTH = 182;
    private static final int OUTSET = 5;

    private PlacementHintHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.EXPERIENCE_BAR,
            AnvilCraftPullWires.of("pole_placement_hint"),
            PlacementHintHud::render
        );
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) return;
        if (minecraft.screen != null) return;
        if (!AnvilCraftPullWiresClient.CONFIG.graphics.showPolePlacementHint) return;

        LocalPlayer player = minecraft.player;
        // 认杆走 PoleKind：这里原本只认本体那两种，导致手持超远程杆 / 跨维度杆时从不显示提示。
        if (!PoleKind.isPoleItem(player.getMainHandItem())) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos placement = hit.getBlockPos().relative(hit.getDirection());
        if (!withinAutoConnectRange(minecraft, placement)) return;

        int color = 0xFF000000 | AnvilCraftPullWiresClient.CONFIG.graphics.placementHintColor;
        int half = EXPERIENCE_BAR_WIDTH / 2 + OUTSET;
        int centerX = graphics.guiWidth() / 2;
        int top = graphics.guiHeight() - 30;
        for (int row = 0; row < ROWS; row++) {
            // 与本体相反：尖角朝外，形成 >< 而不是 <>。
            int inset = ROWS - 1 - Math.abs(row - ROWS / 2);
            graphics.fill(centerX - half - inset, top + row, centerX - half - inset + 2, top + row + 1, color);
            graphics.fill(centerX + half + inset - 2, top + row, centerX + half + inset, top + row + 1, color);
        }
    }

    /**
     * 待放置位置是否落在某根既有杆的 autoR 内。
     *
     * <p>客户端没有完整的杆清单，因此用两个来源近似：已同步的链路端点（精确）
     * 与本体电网的覆盖判定（兜底，覆盖那些还没建立任何链路的杆）。</p>
     */
    private static boolean withinAutoConnectRange(Minecraft minecraft, BlockPos placement) {
        int range = PoleKind.autoConnectRangeOfItem(minecraft.player.getMainHandItem());
        Vec3 center = Vec3.atCenterOf(placement);
        for (LinkDefinition definition : ManualLinkClientState.links()) {
            if (!definition.master().dimension().equals(minecraft.level.dimension())) continue;
            if (Vec3.atCenterOf(definition.master().pos()).distanceTo(center) <= range) return true;
            if (Vec3.atCenterOf(definition.slave().pos()).distanceTo(center) <= range) return true;
        }
        return dev.dubhe.anvilcraft.api.power.SimplePowerGrid.findPowerGrid(placement).isPresent();
    }
}