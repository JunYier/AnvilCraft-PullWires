package dev.anvilcraft.pullwires.client;

import com.google.gson.annotations.SerializedName;
import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import net.neoforged.fml.config.ModConfig;

/**
 * 客户端配置。
 *
 * <p>分组与命名对齐本体 {@code AnvilCraftClientConfig}：
 * {@code Graphics & Rendering} / {@code UI & HUD} / {@code Effects & Sounds}。</p>
 */
@Config(name = AnvilCraftPullWires.MOD_ID, type = ModConfig.Type.CLIENT)
public class AddonClientConfig {
    @SerializedName("Graphics & Rendering")
    @Comment("Adjusts settings related to power line rendering and placement hints")
    @CollapsibleObject
    public GraphicsSettings graphics = new GraphicsSettings();

    @SerializedName("UI & HUD")
    @Comment("Adjusts settings related to the connection list and on-screen information")
    @CollapsibleObject
    public UiSettings ui = new UiSettings();

    @SerializedName("Effects & Sounds")
    @Comment("Adjusts settings related to particles and audio-visual feedback")
    @CollapsibleObject
    public EffectsSettings effects = new EffectsSettings();

    public static class GraphicsSettings {
        @Comment("Controls whether to render the power lines created by this mod")
        public boolean renderManualLinkLines = true;

        @Comment("Line color for ACTIVE links (hex RGB, e.g. 0x66CCFF)")
        @BoundedDiscrete(min = 0, max = 0xFFFFFF)
        public int colorActive = 0x66CCFF;

        @Comment("Line color for INACTIVE links (a pole is offline or not in a grid)")
        @BoundedDiscrete(min = 0, max = 0xFFFFFF)
        public int colorInactive = 0x808080;

        @Comment("Line color for SUSPENDED links (shed by the deficit circuit breaker)")
        @BoundedDiscrete(min = 0, max = 0xFFFFFF)
        public int colorSuspended = 0xFF3B30;

        @Comment("Line color for PENDING links (waiting for a reconnect verdict)")
        @BoundedDiscrete(min = 0, max = 0xFFFFFF)
        public int colorPending = 0xFFD700;

        @Comment(
            """
            Controls whether to draw a green >< bracket on the experience bar
            when the pole in hand is aimed within the auto-connect range of an existing pole
            """
        )
        public boolean showPolePlacementHint = true;

        @Comment("Color of the placement hint bracket (hex RGB)")
        @BoundedDiscrete(min = 0, max = 0xFFFFFF)
        public int placementHintColor = 0x55FF55;
    }

    public static class UiSettings {
        @Comment("Controls whether the connection list stays on screen and updates in real time")
        public boolean connectionListAlwaysOnDisplay = false;

        @Comment("Maximum number of rows the on-screen connection list shows")
        @BoundedDiscrete(min = 1, max = 64)
        public int connectionListMaxRows = 10;

        @Comment("X position of the connection list (in pixels)")
        public int connectionListX = 8;

        @Comment("Y position of the connection list (in pixels)")
        public int connectionListY = 8;

        @Comment("Scale of the connection list")
        @BoundedDiscrete(min = 0.25, max = 4.0)
        public float connectionListScale = 1.0f;

        @Comment(
            """
            How often the particle beams of all links are regenerated, in ticks
            Only links within the same dimension are drawn
            """
        )
        @BoundedDiscrete(min = 20, max = 7200)
        public int particleBeamInterval = 100;
    }

    public static class EffectsSettings {
        @Comment("Controls whether links are visualized with particle beams")
        public boolean linkParticleBeam = true;
    }
}
