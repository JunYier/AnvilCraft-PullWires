package dev.anvilcraft.pullwires;

import com.google.gson.annotations.SerializedName;
import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.power.link.PolePriority;
import dev.anvilcraft.pullwires.power.link.PoleRangeRule;
import dev.dubhe.anvilcraft.AnvilCraft;

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
        //
        // 连接范围存的是「本体 R 的倍数」而不是方块数：本体的供电范围 R 是玩家可改的
        // （machines.powerTransmitterRange 默认 8），写成方块数的话，玩家一调大 R，
        // 旧存档里已经放好的杆就会出现 R_supply > R_auto 的越界并报错。
        // 换算规则见 PoleRangeRule。

        @SerializedName("Transmission Pole")
        @Comment("Ranges of AnvilCraft's own Transmission Pole, as multiples of its own supply range R")
        @CollapsibleObject
        public BodyPoleRanges transmissionPole = BodyPoleRanges.of(1.5, 2.0);

        @SerializedName("Remote Transmission Pole")
        @Comment("Ranges of AnvilCraft's own Remote Transmission Pole, as multiples of its own supply range R")
        @CollapsibleObject
        public BodyPoleRanges remoteTransmissionPole = BodyPoleRanges.of(1.5, 2.0);

        @SerializedName("Ultra Long Range Pole")
        @Comment(
            """
            Ranges of the Ultra Long Range Pole
            It supplies almost nothing; its connection ranges are multiples of AnvilCraft's Transmission Pole R
            So the default 32x / 64x means 256 / 512 blocks while R stays at its default of 8
            """
        )
        @CollapsibleObject
        public AddonPoleRanges ultraLongRangePole = AddonPoleRanges.of(2, 32.0, 64.0);

        @SerializedName("Cross Dimensional Pole")
        @Comment("Ranges of the Cross Dimensional Pole: it supplies nothing and never auto-connects; linking only")
        @CollapsibleObject
        public AddonPoleRanges crossDimensionalPole = AddonPoleRanges.of(0, 0.0, 0.0);

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
            Whether a placed Cross Dimensional Pole keeps its own single chunk loaded
            Uses AnvilCraft's own Overseer chunk loading (level 0 = exactly one chunk, no random tick)
            This is what allows a cross dimensional link to keep settling while nobody is there
            """
        )
        public boolean crossDimensionalPoleForceload = true;

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
            Where the red segment of the distance bar ends, as a multiple of manualConnectRange
            The bar shows green up to manualConnectRange and red up to manualConnectRange * this value
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

        /**
         * 本体那两种杆的范围：只放本模组负责的两个倍数。
         *
         * <p>供电范围 R 仍由本体配置提供（我们无法把自己的界面项指过去），
         * 因此倍数的基准就是<b>该杆型自己的 R</b>，默认 1.5x 即 8 → 12，2x 即 16。</p>
         */
        public static class BodyPoleRanges {
            @Comment("Auto-connect range as a multiple of this pole's own supply range R (1.0-2.0)")
            @BoundedDiscrete(min = 1.0, max = 2.0)
            public double autoConnectMultiplier = 1.5;

            @Comment(
                """
                Manual connect range as a multiple of this pole's own supply range R (1.0-3.0)
                Values below the auto-connect multiplier are raised to it, so R_auto <= R_manual always holds
                """
            )
            @BoundedDiscrete(min = 1.0, max = 3.0)
            public double manualConnectMultiplier = 2.0;

            static BodyPoleRanges of(double autoConnectMultiplier, double manualConnectMultiplier) {
                BodyPoleRanges ranges = new BodyPoleRanges();
                ranges.autoConnectMultiplier = autoConnectMultiplier;
                ranges.manualConnectMultiplier = manualConnectMultiplier;
                return ranges;
            }
        }

        /**
         * 本模组自己的杆：供电范围也是我们的配置。
         *
         * <p>连接范围的倍数基准取<b>本体最基本的输电杆 R</b>——本体没有这两种杆对应的 R，
         * 取基础那一档最不容易误解。</p>
         */
        public static class AddonPoleRanges {
            @Comment("Supply range of this pole type, in blocks (0 = supplies nothing)")
            @BoundedDiscrete(min = 0, max = 8)
            public int supplyRange;

            @Comment("Auto-connect range as a multiple of AnvilCraft's Transmission Pole R (0 = never auto-connects)")
            @BoundedDiscrete(min = 0.0, max = 64.0)
            public double autoConnectMultiplier;

            @Comment("Manual connect range as a multiple of that R (0 = manual linking disabled)")
            @BoundedDiscrete(min = 0.0, max = 64.0)
            public double manualConnectMultiplier;

            static AddonPoleRanges of(int supplyRange, double autoConnectMultiplier, double manualConnectMultiplier) {
                AddonPoleRanges ranges = new AddonPoleRanges();
                ranges.supplyRange = supplyRange;
                ranges.autoConnectMultiplier = autoConnectMultiplier;
                ranges.manualConnectMultiplier = manualConnectMultiplier;
                return ranges;
            }
        }

        /**
         * 按杆种类解析三档范围。这是「按种类取值」的<b>唯一入口</b>，调用方不该再直接读字段。
         */
        public PoleRangeRule.Ranges rangesFor(PoleKind kind) {
            Spec spec = specFor(kind);
            return PoleRangeRule.resolve(
                spec.supplyRange(), spec.base(), spec.autoMultiplier(), spec.manualMultiplier()
            );
        }

        /** 一种杆的原始配置：自身供电范围、倍数基准、两个倍数。 */
        private record Spec(int supplyRange, int base, double autoMultiplier, double manualMultiplier) {
        }

        private Spec specFor(PoleKind kind) {
            // 本体那两种杆：供电范围与倍数基准都取本体配置里对应的那一档。
            int transmissionR = AnvilCraft.CONFIG.machines.powerTransmitterRange;
            int remoteR = AnvilCraft.CONFIG.machines.remotePowerTransmitterRange;
            return switch (kind) {
                case TRANSMISSION -> new Spec(
                    transmissionR, transmissionR,
                    this.transmissionPole.autoConnectMultiplier,
                    this.transmissionPole.manualConnectMultiplier
                );
                case REMOTE -> new Spec(
                    remoteR, remoteR,
                    this.remoteTransmissionPole.autoConnectMultiplier,
                    this.remoteTransmissionPole.manualConnectMultiplier
                );
                case ULTRA_LONG_RANGE -> new Spec(
                    this.ultraLongRangePole.supplyRange, transmissionR,
                    this.ultraLongRangePole.autoConnectMultiplier,
                    this.ultraLongRangePole.manualConnectMultiplier
                );
                case CROSS_DIMENSIONAL -> new Spec(
                    this.crossDimensionalPole.supplyRange, transmissionR,
                    this.crossDimensionalPole.autoConnectMultiplier,
                    this.crossDimensionalPole.manualConnectMultiplier
                );
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
        @Comment("Whether a bound disk in the offhand may trigger a link attempt when a pole is placed")
        public boolean allowDiskBinding = true;
    }
}
