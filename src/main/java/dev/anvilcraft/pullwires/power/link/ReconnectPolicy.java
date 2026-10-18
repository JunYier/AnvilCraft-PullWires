package dev.anvilcraft.pullwires.power.link;

import java.util.Comparator;
import java.util.Optional;

/**
 * 挂起后的重连裁决：<b>申请排序</b>与<b>批不批准</b>。
 *
 * <p>纯整数运算，不依赖任何游戏类型，可脱离游戏单独验证。</p>
 *
 * <h2>为什么需要它</h2>
 * <p>原来的规则是「挂起后冷却够长、主端账面不为负就自己复活」。在真实玩法里这条规则是死的：
 * 高负载的从端被主端断开之后，只要没人手动重置那根杆，它就<b>永远没有机会再次申请</b>——
 * 而这恰好是最需要自动恢复的场景。</p>
 *
 * <h2>模型</h2>
 * <ol>
 *   <li>从端被挂起后，每 {@code reconnectRequestInterval} 拍向主端发一次申请；</li>
 *   <li>主端用<b>最近一段时间（20 拍窗口）的平均富余</b>评估这次申请能不能满足——
 *       用窗口平均值而不是瞬时值，是因为瞬时值会抖，一抖就批、一批就再亏，来回震荡；</li>
 *   <li>多个从端同时申请时就按下面的排序逐个裁决，先到先得。</li>
 * </ol>
 *
 * <h2>排序：主因素 + 副因素 + 固定的时间键</h2>
 * <p>玩家可以决定先按哪个因素排（优先级 / 申请量），以及主、副因素各自升序还是降序。
 * <b>时间永远升序且不可调</b>——先建立的链路先被考虑，这是直觉上唯一说得通的默认。</p>
 *
 * @param primary           主排序因素
 * @param primaryAscending  主因素是否升序
 * @param secondaryAscending 副因素是否升序（副因素自动取「主因素之外的那一个」）
 */
public record ReconnectPolicy(Factor primary, boolean primaryAscending, boolean secondaryAscending)
    implements Comparator<ReconnectPolicy.Request> {
    /** 排序因素。 */
    public enum Factor {
        /** 按从端所在杆的优先级（数值越小越慷慨）。 */
        PRIORITY,
        /** 按从端的平均缺口（申请量）。 */
        DEMAND
    }

    /** 默认：优先级优先、升序；副因素（申请量）也升序——即「先照顾最慷慨、需求最小的」。 */
    public static final ReconnectPolicy DEFAULT = new ReconnectPolicy(Factor.PRIORITY, true, true);

    /**
     * 由三个开关拼出一份策略：<b>这是「配置项 / 网络包 → 策略」的唯一映射</b>。
     *
     * <p>存档级默认排序、每根杆自己的设定、以及客户端提交的改动全部经这一个入口，
     * 因此不存在「某处把 <i>需求优先</i> 映射成了别的因素」的可能。
     * 三个开关都是布尔量，所以它可以脱离游戏断言。</p>
     *
     * @param demandFirst       主因素是不是「需求」（{@code false} = 优先级）
     * @param primaryAscending  主因素是否升序
     * @param secondaryAscending 副因素是否升序
     */
    public static ReconnectPolicy ofFlags(boolean demandFirst, boolean primaryAscending, boolean secondaryAscending) {
        return new ReconnectPolicy(
            demandFirst ? Factor.DEMAND : Factor.PRIORITY,
            primaryAscending,
            secondaryAscending
        );
    }

    /**
     * 解析「本杆自己的排序设定 vs 存档默认」。
     *
     * <p>本杆尚未配置（空）时用存档默认；否则一律用它自己那份。
     * 服务端 {@code WireLinkRegistry.policyOf} 与客户端镜像 {@code ManualLinkClientState.policyOf}
     * <b>共用这一条规则</b>，因此页面上显示的顺序与主端实际裁决用的顺序必然同源。</p>
     *
     * <p>V1.12.13 起这只是「补齐之前」的兜底读法：一进世界的头一次结算就会
     * {@link #materialize} 把尚未配置的杆补成一份存档默认快照，之后 <i>本杆自己的设定</i> 恒定非空。</p>
     */
    public static ReconnectPolicy resolve(Optional<ReconnectPolicy> stored, ReconnectPolicy fallback) {
        return stored.orElse(fallback);
    }

    /**
     * 补齐：把「这根杆还没有自己的排序设定」物化成一份存档默认快照。
     *
     * <p><b>为什么需要它</b>：V1.12.13 去掉了页面上的「覆盖 / 跟随默认」开关，每根杆都<b>自己</b>持有一份
     * 排序设定。空值因此只剩一个含义——<b>这根杆还没被配置过</b>；服务端在每拍结算的
     * {@code PowerInterconnectManager.settle()} 里给参与链路的杆补上一份存档默认
     * （见 {@code WireLinkRegistry.backfillReconnectPolicies}）。</p>
     *
     * <p><b>幂等性来自这里</b>：已配置（非空）时原样返回 {@code stored}，不做任何替换，
     * 因此再补一次得到的就是它自己，玩家的调整不会被存档默认盖掉；未配置时返回一份
     * <i>与存档默认相等</i> 的值，补完即非空，第二次进来走的是「已配置」那条分支。
     * 这两条合起来就是「补齐只对尚未配置的杆生效，且只生效一次」。</p>
     *
     * <p>纯 {@code Optional} 运算，不碰任何游戏类型，所以这条规则可以脱离游戏断言
     * （{@code ReconnectPolicySelfCheck}）。</p>
     *
     * @param stored          该杆当前存着的设定；空 = 尚未配置
     * @param saveWideDefault 存档级默认，取自 {@code AddonConfig.Settlement.reconnectSort}
     * @return 补齐后应当存下的设定；已配置时与 {@code stored} 相等
     */
    public static Optional<ReconnectPolicy> materialize(
        Optional<ReconnectPolicy> stored, ReconnectPolicy saveWideDefault
    ) {
        return stored.isPresent() ? stored : Optional.of(saveWideDefault);
    }

    /** 主因素是不是「需求」。 */
    public boolean demandFirst() {
        return this.primary == Factor.DEMAND;
    }

    /** 副因素 = 主因素之外的那一个。 */
    public Factor secondary() {
        return this.primary == Factor.PRIORITY ? Factor.DEMAND : Factor.PRIORITY;
    }

    // ---------------------------------------------------------------- 页面上的三处开关
    // 都只改自己关心的那一个字段：切换主因素不得顺手把两个方向开关也改掉。

    /** 换主因素（优先级 ⇄ 需求）；两个方向开关原样保留。 */
    public ReconnectPolicy withPrimary(Factor newPrimary) {
        return new ReconnectPolicy(newPrimary, this.primaryAscending, this.secondaryAscending);
    }

    /** 在主因素的两个取值之间切换。 */
    public ReconnectPolicy toggledPrimary() {
        return this.withPrimary(this.secondary());
    }

    /** 设定主因素的方向。 */
    public ReconnectPolicy withPrimaryAscending(boolean ascending) {
        return new ReconnectPolicy(this.primary, ascending, this.secondaryAscending);
    }

    /** 翻转主因素的方向。 */
    public ReconnectPolicy toggledPrimaryAscending() {
        return this.withPrimaryAscending(!this.primaryAscending);
    }

    /** 设定副因素的方向。 */
    public ReconnectPolicy withSecondaryAscending(boolean ascending) {
        return new ReconnectPolicy(this.primary, this.primaryAscending, ascending);
    }

    /** 翻转副因素的方向。 */
    public ReconnectPolicy toggledSecondaryAscending() {
        return this.withSecondaryAscending(!this.secondaryAscending);
    }

    /** 三键排序的最终键：时间。固定升序，不参与调节。 */
    public static final long TIME_IS_ASCENDING = 1L;

    /**
     * 一次重连申请。
     *
     * @param priority  从端所在杆的优先级
     * @param demand    从端的平均缺口，恒为非负
     * @param createdAt 链路建立序号，越小越早
     */
    public record Request(int priority, int demand, long createdAt) {
    }

    /** 比较两个申请；返回值 &lt; 0 表示 {@code a} 先被裁决。 */
    public int compare(Request a, Request b) {
        int byPrimary = compareFactor(a, b, this.primary);
        if (!this.primaryAscending) byPrimary = -byPrimary;
        if (byPrimary != 0) return byPrimary;

        int bySecondary = compareFactor(a, b, this.secondary());
        if (!this.secondaryAscending) bySecondary = -bySecondary;
        if (bySecondary != 0) return bySecondary;

        return Long.compare(a.createdAt(), b.createdAt());
    }

    private static int compareFactor(Request a, Request b, Factor factor) {
        return factor == Factor.PRIORITY
            ? Integer.compare(a.priority(), b.priority())
            : Integer.compare(a.demand(), b.demand());
    }

    /**
     * 主端要不要批准这次申请。
     *
     * <p>判据是<b>最近一段时间的平均富余够不够覆盖从端的平均缺口</b>。
     * 两者都取平均：瞬时值一抖就批、一批就再亏，会来回震荡；用窗口平均值相当于
     * 「你最近一直是这个水平，我才答应」。</p>
     *
     * @param masterAverageSurplus 主端最近一段时间的平均富余（20 拍窗口）
     * @param demand               从端的平均缺口，非正数一律不批
     */
    public static boolean approve(int masterAverageSurplus, int demand) {
        return demand > 0 && masterAverageSurplus >= demand;
    }
}
