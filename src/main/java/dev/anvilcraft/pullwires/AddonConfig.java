package dev.anvilcraft.pullwires;

import com.google.gson.annotations.SerializedName;
import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.power.link.PolePriority;
import dev.anvilcraft.pullwires.power.link.PoleRangeRule;
import dev.anvilcraft.pullwires.power.link.ReconnectPolicy;
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
        // 距离一律取「长边长度」max(|dx|,|dy|,|dz|)，不是直线距离：自动组网与手动连接共用这一个标量，
        // 同量纲才能保证 R_supply ≤ R_auto ≤ R_manual。
        //
        // 默认 3x / 4x 的依据（V1.12.15）：本体自己的自动组网是**包围盒相交**——
        // getShape() 半宽 R+0.5，两盒相交条件 |d| < 2R+1 = 17，所以两根本体杆之间的
        // **最大整数自动连接距离是 16 格**（R=8 时）。若我们的 autoR 只给 1.5x=12，
        // 就会出现「本体自己连得上的两根杆，在本模组里反而连不上」的倒退。3x=24 高于 16，
        // 保证不会比本体更严格；manualR 取 4x=32，仍严格大于 autoR。

        @SerializedName("Transmission Pole")
        @Comment("Ranges of AnvilCraft's own Transmission Pole, as multiples of its own supply range R")
        @CollapsibleObject
        public BodyPoleRanges transmissionPole = BodyPoleRanges.of(3.0, 4.0);

        @SerializedName("Remote Transmission Pole")
        @Comment("Ranges of AnvilCraft's own Remote Transmission Pole, as multiples of its own supply range R")
        @CollapsibleObject
        public BodyPoleRanges remoteTransmissionPole = BodyPoleRanges.of(3.0, 4.0);

        @SerializedName("Ultra Long Range Pole")
        @Comment(
            """
            Ranges of the Ultra Long Range Pole
            It supplies almost nothing; its connection ranges are multiples of AnvilCraft's Transmission Pole R
            So the default 16x / 32x means 128 / 256 blocks while R stays at its default of 8
            """
        )
        @CollapsibleObject
        public AddonPoleRanges ultraLongRangePole = AddonPoleRanges.of(2, 16.0, 32.0);

        @SerializedName("Cross Dimensional Pole")
        @Comment("Ranges of the Cross Dimensional Pole. It is an ordinary pole with supply 0 and auto-connect 0, so it only joins a grid it physically touches; its manual range is unlimited and it may link across dimensions")
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
         * 因此倍数的基准就是<b>该杆型自己的 R</b>，默认 3x 即 24，4x 即 32。</p>
         */
        public static class BodyPoleRanges {
            @Comment("Auto-connect range as a multiple of this pole's own supply range R (1.0-4.0)")
            @BoundedDiscrete(min = 1.0, max = 4.0)
            public double autoConnectMultiplier = 3.0;

            @Comment(
                """
                Manual connect range as a multiple of this pole's own supply range R (1.0-8.0)
                Values below the auto-connect multiplier are raised to it, so R_auto <= R_manual always holds
                """
            )
            @BoundedDiscrete(min = 1.0, max = 8.0)
            public double manualConnectMultiplier = 4.0;

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

            @Comment("Auto-connect range as a multiple of AnvilCraft's Transmission Pole R. 0 means a zero-size range, i.e. it only auto-connects with a pole it physically touches")
            @BoundedDiscrete(min = 0.0, max = 64.0)
            public double autoConnectMultiplier;

            @Comment("Manual connect range as a multiple of that R. 0 means the pole is subject to no distance limit at all")
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

        @Comment(
            """
            How often a suspended slave pole requests a reconnect, in ticks
            The master approves when its average surplus over the recent window covers the slave's average demand
            """
        )
        @BoundedDiscrete(min = 20, max = 7200)
        public int reconnectRequestInterval = 200;

        @SerializedName("Reconnect Sort")
        @Comment(
            """
            Save-wide default ordering of reconnect requests
            Copied once into every pole that has no ordering of its own yet, on the first settlement tick after the world loads
            Poles that already have their own ordering are skipped and never overwritten
            Any pole can be retuned in its own settings page (Anvil Hammer + the pole config hotkey + right-click)
            The time factor is always ascending and is not configurable
            """
        )
        @CollapsibleObject
        public ReconnectSort reconnectSort = new ReconnectSort();

        /**
         * 存档级默认的重连申请排序：一个「主因素是谁」的开关加两个方向开关。
         *
         * <p><b>为什么不用 {@link ReconnectPolicy.Factor} 这个枚举做配置项</b>：配置系统只为实现了
         * {@code TranslatableEnum} 的枚举生成语言键，普通枚举在配置界面里会直接显示原始名字。
         * 三个布尔量既没有这个问题，也不依赖配置系统对枚举的支持程度。</p>
         *
         * <p>映射成策略只有 {@link ReconnectPolicy#ofFlags} 一条路（有自检）。</p>
         */
        public static class ReconnectSort {
            @Comment(
                """
                true  = rank reconnect requests by demand (the slave's average deficit) first
                false = rank them by priority (the slave pole's settlement priority) first
                """
            )
            public boolean demandFirst = false;

            @Comment(
                """
                Whether the primary factor is ascending
                Ascending means small values are served first
                """
            )
            public boolean primaryAscending = true;

            @Comment(
                """
                Whether the secondary factor is ascending
                The secondary factor is always the one that is not the primary factor
                """
            )
            public boolean secondaryAscending = true;

            /** 本组配置对应的策略。 */
            public ReconnectPolicy policy() {
                return ReconnectPolicy.ofFlags(this.demandFirst, this.primaryAscending, this.secondaryAscending);
            }
        }
    }

    public static class Linking {
        @Comment("Whether a bound disk in the offhand may trigger a link attempt when a pole is placed")
        public boolean allowDiskBinding = true;
    }
}
