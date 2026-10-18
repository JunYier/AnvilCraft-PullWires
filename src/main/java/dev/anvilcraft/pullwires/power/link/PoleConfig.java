package dev.anvilcraft.pullwires.power.link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * 输电杆的玩家配置，<b>会写入存档</b>。
 *
 * <h2>只有这一份状态</h2>
 * <p>重连申请的排序设定也落在本记录里，因此它天然满足三件事：随存档持久化、随
 * {@code SyncRegistryPacket}（它整体同步 {@code PoleEntry}）下发、以及服务端与客户端读的是同一个字段。
 * 客户端界面上显示的排序就是这里读出来的，不存在可写的客户端镜像。</p>
 *
 * @param priority        结算优先级，0–15（见 {@link PolePriority}），越低越慷慨
 * @param locked          是否忽略外部红石信号造成的 {@code SWITCH} 切换
 * @param reconnectPolicy 本杆**自己**的重连排序设定；<b>空 = 这根杆还没被配置过</b>
 *                        （进世界后由 {@code PowerInterconnectManager} 的头一次结算补上一份
 *                        存档默认快照，见 {@link ReconnectPolicy#materialize}）。
 *                        解析规则只有 {@link ReconnectPolicy#resolve} 一处。
 */
public record PoleConfig(int priority, boolean locked, Optional<ReconnectPolicy> reconnectPolicy) {
    /**
     * 排序设定的编解码。
     *
     * <p><b>为什么不写在 {@link ReconnectPolicy} 里</b>：那是一个零依赖的纯逻辑类，
     * 自检直接引用它；一旦它带上 {@code Codec}，脱离游戏的 {@code verifyAddon} 就会被迫加载
     * Mojang 的 serialization 库（{@link PolePriority} 的注释记的就是这条教训）。
     * 因此「纯逻辑」与「怎么存」分开：存法留在本类。</p>
     */
    public static final Codec<ReconnectPolicy> RECONNECT_POLICY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        // 宽容解析：认不出的名字退回优先级，与 decodeLenient 的「坏一条跳一条」一致，不让存档读失败。
        Codec.STRING.xmap(
            name -> "demand".equals(name) ? ReconnectPolicy.Factor.DEMAND : ReconnectPolicy.Factor.PRIORITY,
            factor -> factor == ReconnectPolicy.Factor.DEMAND ? "demand" : "priority"
        ).optionalFieldOf("primary", ReconnectPolicy.Factor.PRIORITY).forGetter(ReconnectPolicy::primary),
        Codec.BOOL.optionalFieldOf("primary_ascending", true).forGetter(ReconnectPolicy::primaryAscending),
        Codec.BOOL.optionalFieldOf("secondary_ascending", true).forGetter(ReconnectPolicy::secondaryAscending)
    ).apply(instance, ReconnectPolicy::new));

    public static final Codec<PoleConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("priority", PolePriority.DEFAULT).forGetter(PoleConfig::priority),
        Codec.BOOL.optionalFieldOf("locked", false).forGetter(PoleConfig::locked),
        // 旧存档没有这个键：空 = 这根杆还没被配置过，进世界后会被补上一份存档默认（行为与加这个字段之前一致）。
        RECONNECT_POLICY_CODEC.optionalFieldOf("reconnect_policy").forGetter(PoleConfig::reconnectPolicy)
    ).apply(instance, PoleConfig::new));

    public PoleConfig {
        priority = PolePriority.clamp(priority);
        reconnectPolicy = reconnectPolicy == null ? Optional.empty() : reconnectPolicy;
    }

    public PoleConfig withPriority(int newPriority) {
        return new PoleConfig(PolePriority.clamp(newPriority), this.locked, this.reconnectPolicy);
    }

    public PoleConfig withLocked(boolean newLocked) {
        return new PoleConfig(this.priority, newLocked, this.reconnectPolicy);
    }

    /** 写入本杆自己的排序设定；排序以外的字段原样保留。 */
    public PoleConfig withReconnectPolicy(Optional<ReconnectPolicy> newPolicy) {
        return new PoleConfig(this.priority, this.locked, newPolicy == null ? Optional.empty() : newPolicy);
    }

    /**
     * 本杆实际生效的排序设定。
     *
     * @param fallback 存档级默认；本杆尚未配置时原样返回它
     */
    public ReconnectPolicy effectiveReconnectPolicy(ReconnectPolicy fallback) {
        return ReconnectPolicy.resolve(this.reconnectPolicy, fallback);
    }
}
