package dev.anvilcraft.pullwires.power.link;

/**
 * 手动连接过程中，瞄准点到目标杆的距离所处的区间。
 *
 * <p>纯逻辑，不依赖游戏类型，方便单独验证。</p>
 */
public enum ManualLinkFeedback {
    /** 在手动连接范围内，可以放置。 */
    IN_RANGE,
    /** 已超出手动连接范围但还没到放弃距离，可以放但会被拒绝。 */
    OUT_OF_RANGE,
    /** 超出放弃距离，待连接状态应当被清除。 */
    CANCEL
}
