package dev.anvilcraft.pullwires;

import com.google.gson.annotations.SerializedName;
import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.anvilcraft.pullwires.power.link.PoleKind;
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
        // ---------------------------------------------------------------- 按杆种类划分的三档范围
        // 与本体一致：范围按「杆子种类」取值，而不是全局一套。
        // 本体那两种杆的「供电范围」仍由本体配置提供（我们无法把自己的界面项指过去），
        // 因此这里只放本模组负责的自动连接范围与手动连接范围。

        @SerializedName("Transmission Pole")
        @Comment("Ranges of AnvilCraft's own Transmission Pole. Its supply range stays in AnvilCraft's config.")
        @CollapsibleObject
        public PoleRanges transmissionPole = PoleRanges.of(12, 16);

        @SerializedName("Remote Transmission Pole")
        @Comment("Ranges of AnvilCraft's own Remote Transmission Pole. Its supply range stays in AnvilCraft's config.")
        @CollapsibleObject
        public PoleRanges remoteTransmissionPole = PoleRanges.of(12, 16);

        @SerializedName("Ultra Long Range Pole")
        @Comment("Ranges of the Ultra Long Range Pole: tiny supply range, huge connection ranges")
        @CollapsibleObject
        public PoleRanges ultraLongRangePole = PoleRanges.of(256, 256);

        @SerializedName("Cross Dimensional Pole")
        @Comment("Ranges of the Cross Dimensional Pole: it supplies nothing and never auto-connects; disk linking only")
        @CollapsibleObject
        public PoleRanges crossDimensionalPole = PoleRanges.of(0, 0);

        @Comment(
            """
            Whether a link may be established between poles that are in different dimensions
            A link crosses dimensions only when BOTH ends are Cross Dimensional Poles
            Turn this off to keep them as ordinary no-supply poles that only link within one dimension
            """
        )
        public boolean crossDimensionalPoleEnabled = true;

        @Comment(
            """
            Power loss coefficient of a link that crosses dimensions (0.0-1.0)
            The slave receives amount * (1 - loss); the difference is dissipated and belongs to nobody
            Applies only when the two ends are in different dimensions
            """
        )
        @BoundedDiscrete(min = 0.0, max = 1.0)
        public double crossDimensionalPoleLoss = 0.0;

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

        /** 一种杆的三档范围里本模组负责的两档；0 表示不使用该档。 */
        public static class PoleRanges {
            @Comment("Auto-connect range in blocks (straight-line distance). 0 = this pole type never auto-connects")
            @BoundedDiscrete(min = 0, max = 512)
            public int autoConnectRange;

            @Comment("Maximum manual connect distance in blocks. 0 = manual linking disabled for this pole type")
            @BoundedDiscrete(min = 0, max = 512)
            public int manualConnectRange;

            static PoleRanges of(int autoConnectRange, int manualConnectRange) {
                PoleRanges ranges = new PoleRanges();
                ranges.autoConnectRange = autoConnectRange;
                ranges.manualConnectRange = manualConnectRange;
                return ranges;
            }
        }

        /** 按杆种类取范围。这是「按种类划分」的唯一入口，调用方不该再直接读配置字段。 */
        public PoleRanges rangesFor(PoleKind kind) {
            return switch (kind) {
                case TRANSMISSION -> this.transmissionPole;
                case REMOTE -> this.remoteTransmissionPole;
                case ULTRA_LONG_RANGE -> this.ultraLongRangePole;
                case CROSS_DIMENSIONAL -> this.crossDimensionalPole;
            };
        }
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
