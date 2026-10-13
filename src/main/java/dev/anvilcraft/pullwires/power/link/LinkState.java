package dev.anvilcraft.pullwires.power.link;

/**
 * 链路的运行时状态。<b>不写入存档</b>：开机后由两端的入网情况与开关状态重新推导。
 *
 * <p>{@link #INACTIVE} 与 {@link #SUSPENDED} 必须区分开——前者是「暂时不在线」，
 * 恢复后自动接通；后者是「被主动切断」，需要主端裁决重连或一次开关重启。
 * 混为一谈会让断电恢复后链路被无条件接通，失去亏电断路保护的意义。</p>
 */
public enum LinkState {
    /** 两端均已入网且未被挂起，正常结算。 */
    ACTIVE,
    /** 任一端未入网（杆 {@code SWITCH=OFF}、区块未加载）。重新入网后自动回到 {@link #ACTIVE}。 */
    INACTIVE,
    /** 被主端的亏电断路挂起。需要主端同意重连，或一次开关重启。 */
    SUSPENDED,
    /** 从端已发起重连申请，等待主端批准。 */
    PENDING
}
