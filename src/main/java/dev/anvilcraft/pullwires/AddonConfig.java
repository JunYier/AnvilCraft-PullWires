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
            Controls the auto-connect range of Transmission Poles (in blocks)
            Poles within this range join the same grid, but this range does NOT supply power:
            generators, consumers and storages still only join by the pole's own range
            """
        )
        @BoundedDiscrete(min = 1, max = 128)
        public int autoConnectRange = 16;

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
