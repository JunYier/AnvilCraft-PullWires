package dev.anvilcraft.pullwires;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.anvilcraft.pullwires.power.link.PolePriority;

@Config(name = AnvilCraftPullWires.MOD_ID)
public class AddonConfig {
    @Comment("Adjusts settings related to power grid links")
    @CollapsibleObject
    public PowerLink powerLink = new PowerLink();

    public static class PowerLink {
        @Comment(
            """
            Controls the auto-connect range of Transmission Poles (in blocks, straight-line distance)
            Defaults to 1.5x the pole's own range from AnvilCraft's config
            Poles within this range join the same grid, but this range does NOT supply power:
            generators, consumers and storages still only join by the pole's own range
            """
        )
        @BoundedDiscrete(min = 1, max = 128)
        public int autoConnectRange = 12;

        @Comment(
            """
            Controls the maximum straight-line distance of a manual connection (in blocks)
            Defaults to 2x the pole's own range from AnvilCraft's config
            Measured between the top segments of the two poles, not between bounding boxes
            """
        )
        @BoundedDiscrete(min = 1, max = 256)
        public int manualConnectRange = 16;

        @Comment(
            """
            Controls when a pending manual connection is dropped (multiplier of manualConnectRange)
            Once the aim goes beyond manualConnectRange * this value, the pending state is cleared
            """
        )
        @BoundedDiscrete(min = 1.0, max = 8.0)
        public double cancelDistanceMultiplier = 2.0;

        @Comment(
            """
            Controls the settlement priority of newly placed Transmission Poles (0-15)
            Lower priority is more generous: it gives power away earlier and is served later
            """
        )
        @BoundedDiscrete(min = PolePriority.MIN, max = PolePriority.MAX)
        public int poleDefaultPriority = PolePriority.DEFAULT;
    }
}
