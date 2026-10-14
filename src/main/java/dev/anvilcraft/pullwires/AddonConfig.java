package dev.anvilcraft.pullwires;

import com.google.gson.annotations.SerializedName;
import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.anvilcraft.pullwires.power.link.PolePriority;

/**
 * 服务端配置。
 *
 * <p>分组思路对齐本体（{@code AnvilCraftServerConfig} 的 {@code World & Physics} /
 * {@code Machines & Logistics} / {@code Equipment & Enchanting} / {@code Commands}）：
 * 按<b>功能域</b>划分顶层分组，而不是把所有键堆在一个 {@code powerLink} 下。</p>
 */
@Config(name = AnvilCraftPullWires.MOD_ID)
public class AddonConfig {
    @SerializedName("Transmission")
    @Comment("Adjusts settings related to connection ranges and redstone behaviour of Transmission Poles")
    @CollapsibleObject
    public Transmission transmission = new Transmission();

    @SerializedName("Settlement")
    @Comment("Adjusts settings related to interconnect settlement, deficit breaking and reconnection")
    @CollapsibleObject
    public Settlement settlement = new Settlement();

    @SerializedName("Linking")
    @Comment("Adjusts settings related to manual linking between poles")
    @CollapsibleObject
    public Linking linking = new Linking();

    public static class Transmission {
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
            Whether flipping a pole's SWITCH from OFF to ON resets its runtime values
            and restores its suspended links, keeping the link definitions intact
            This is the "switch restart" semantics
            """
        )
        public boolean switchRestartResetsRuntime = true;
    }

    public static class Settlement {
        @Comment(
            """
            Controls the settlement priority of newly placed Transmission Poles (0-15)
            Lower priority is served later: a lower number always yields to a higher one
            """
        )
        @BoundedDiscrete(min = PolePriority.MIN, max = PolePriority.MAX)
        public int poleDefaultPriority = PolePriority.DEFAULT;

        @Comment(
            """
            Deficit circuit breaker threshold, in ticks (0 = never auto-suspend)
            When the master pole's grid stays in deficit for this long, its links are suspended
            Links are shed by average net outflow, highest first, re-checked after each shed
            """
        )
        @BoundedDiscrete(min = 0, max = 72000)
        public int manualLinkDeficitTicks = 300;

        @Comment("Reconnect cooldown after a link was suspended, in ticks")
        @BoundedDiscrete(min = 0, max = 7200)
        public int manualLinkReconnectCooldown = 100;

        @Comment("How long the slave's grid must keep working before it may request a reconnect, in ticks")
        @BoundedDiscrete(min = 1, max = 1200)
        public int reconnectStableTicks = 100;

        @Comment("How often a stable slave grid requests a reconnect, in ticks")
        @BoundedDiscrete(min = 20, max = 7200)
        public int reconnectRequestInterval = 100;
    }

    public static class Linking {
        @Comment(
            """
            Whether holding a Transmission Pole and pressing the manual link hotkey on a placed pole
            starts (or stops) the manual linking state
            Turning this off disables that path only; an offhand bound disk still works
            """
        )
        public boolean enableManualLinkByHotkey = true;

        @Comment("Whether a bound disk in the offhand may trigger a link attempt when a pole is placed")
        public boolean allowDiskBinding = true;

        @Comment("Maximum number of links a single pole may master")
        @BoundedDiscrete(min = 1, max = 256)
        public int maxManualLinksPerPole = 32;
    }
}
