package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * 客户端「这条链路该不该画、这个端点该不该冒粒子」的唯一判定入口。
 *
 * <p>电力线渲染与粒子束是两处独立实现，之前各写了一遍「只画同维度」的过滤，
 * 结果两处都漏掉了「跨维度杆任何情况下都不画线」（需求 AC3.2.2）。
 * 判定集中在这里之后，新增杆型或改规则只需要动一处。</p>
 *
 * <h2>为什么可以直接读本地方块状态</h2>
 * <p>端点是不是跨维度杆，看的是它那一格的方块。这个判断只在<b>端点位于本地维度</b>时才会执行，
 * 所以在本地读方块状态是合法且代价极低的——真正跨越维度的链路早在维度比较那一步就被排除了，
 * 不会出现「拿着别的世界的坐标去本地读方块」。</p>
 */
public final class ClientLinkVisibility {
    private ClientLinkVisibility() {
    }

    /** 该端点是否在本地维度里（跨维度的另一端不在，因此它的坐标在本地没有意义）。 */
    public static boolean inLocalLevel(ClientLevel level, PoleKey key) {
        return key.dimension().equals(level.dimension());
    }

    /** 该坐标上是不是一根跨维度输电杆。区块没加载时返回 false（看不到的东西不必处理）。 */
    public static boolean isCrossDimensionalPole(ClientLevel level, BlockPos pos) {
        // 用 isLoaded 而不是 hasChunkAt：后者在 1.21.1 已标记 @Deprecated。
        return level.isLoaded(pos) && PoleKind.of(level.getBlockState(pos)) == PoleKind.CROSS_DIMENSIONAL;
    }

    /**
     * 该链路是否应该在本地世界里画线。
     *
     * <p>两类排除：</p>
     * <ol>
     *   <li>两端不在同一维度——线的另一端在世界之外，画出来只是一根指向空处的线；</li>
     *   <li>任一端是跨维度杆——需求 §5.2 规定跨维度杆<b>任何情况下都不绘制线条</b>，
     *       它用顶端的传送门粒子表达连接态。</li>
     * </ol>
     */
    public static boolean drawsLine(ClientLevel level, LinkDefinition definition) {
        PoleKey master = definition.master();
        PoleKey slave = definition.slave();
        if (!master.dimension().equals(slave.dimension())) return false;
        if (!inLocalLevel(level, master)) return false;
        return !isCrossDimensionalPole(level, master.pos()) && !isCrossDimensionalPole(level, slave.pos());
    }

    /**
     * 该端点是否应当冒传送门粒子。
     *
     * <p>只给<b>已连接（ACTIVE）</b>状态的跨维度杆端点，断开或挂起后立即停止——
     * 这正是「传送门粒子」在本模组里的语义：它替代了线条，成了连接态的唯一可视信号。</p>
     */
    public static boolean emitsPortalParticles(ClientLevel level, PoleKey key, LinkState state) {
        return state == LinkState.ACTIVE && inLocalLevel(level, key) && isCrossDimensionalPole(level, key.pos());
    }
}
