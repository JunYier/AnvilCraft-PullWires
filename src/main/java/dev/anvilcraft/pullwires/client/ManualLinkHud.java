package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Locale;

/**
 * 待连接期间常驻的 HUD：显示当前连接距离与上限。
 *
 * <p>放在准心正下方，颜色随是否在范围内变化，这样不必抬头看聊天栏就知道还能不能放。</p>
 */
public final class ManualLinkHud {
    private ManualLinkHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CROSSHAIR,
            AnvilCraftPullWires.of("manual_link_distance"),
            ManualLinkHud::render
        );
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) return;
        if (minecraft.screen != null) return;

        BlockPos master = ManualLinkClientState.pendingMaster();
        if (master == null) return;
        if (!minecraft.player.getMainHandItem().is(ModBlocks.TRANSMISSION_POLE.get().asItem())) return;

        int range = AnvilCraftPullWires.CONFIG.powerLink.manualConnectRange;
        double distance = ManualLinkAim.distance(minecraft, master);
        boolean inRange = distance <= range;

        MutableComponent amount = Component
            .literal(format(distance))
            .withStyle(inRange ? ChatFormatting.GREEN : ChatFormatting.RED);
        Component text = Component.translatable(
            "hud.anvilcraft_pullwires.manual_link.distance",
            amount,
            Component.literal(String.valueOf(range)).withStyle(ChatFormatting.GRAY)
        );

        int width = minecraft.font.width(text);
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 + 12;
        graphics.drawString(minecraft.font, text, x, y, 0xFFFFFFFF, true);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
