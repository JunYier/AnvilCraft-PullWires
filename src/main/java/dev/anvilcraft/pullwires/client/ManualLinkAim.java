package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.power.link.PoleAnchor;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.util.PoleResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 手动连接的取点：放置预览头部、瞄准杆顶、以及两者之间的锚点。
 *
 * <p>粒子束与 HUD 都用这里，保证「看到的落点」就是「服务端判定用的落点」。
 * 锚点公式本身在 {@link PoleAnchor}（服务端也要用，不能放在客户端类里）。</p>
 */
public final class ManualLinkAim {
    private ManualLinkAim() {
    }

    /** 任意坐标上方 {@code heightOffset} 格处的锚点；见 {@link PoleAnchor#headOf}。 */
    public static Vec3 headOf(BlockPos pos, double heightOffset) {
        return PoleAnchor.headOf(pos, heightOffset);
    }

    /** 顶段方块的杆顶；见 {@link PoleAnchor#masterHead}。 */
    public static Vec3 masterHead(BlockPos topSegment) {
        return PoleAnchor.masterHead(topSegment);
    }

    /**
     * 放置预览轮廓的头部。
     *
     * <p>高度取<b>要放下的那根杆</b>的高度（{@link PoleKind#height()}），不再写死偏移：
     * 输电杆 3 格、远程杆与本模组两种 4 格。判定依据按优先级：</p>
     * <ol>
     *   <li>主/副手拿着本模组认得的杆 → 用那根杆的高度；</li>
     *   <li>手上是磁盘 → 用<b>视角指着的那根杆</b>的高度（这正是「磁盘时要知道盯着的是三格还是四格」）；</li>
     *   <li>都判不出 → 退回四格（四段结构是本模组两种杆与远程杆的共同形态）。</li>
     * </ol>
     */
    public static @Nullable Vec3 placementHead(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos placement = hit.getBlockPos().relative(hit.getDirection());
        return headOf(placement, placementHeight(minecraft, hit.getBlockPos()));
    }

    /** 即将放下的那根杆的高度；见 {@link #placementHead}。 */
    private static double placementHeight(Minecraft minecraft, BlockPos aimedPole) {
        if (minecraft.player != null) {
            int held = PoleKind.heightOfItem(minecraft.player.getMainHandItem());
            if (held <= 0) held = PoleKind.heightOfItem(minecraft.player.getOffhandItem());
            if (held > 0) return held;
        }
        // 磁盘：视角指着的那根杆有多高，就按多高落点。
        if (minecraft.level != null) {
            int aimed = PoleKind.heightOf(minecraft.level.getBlockState(aimedPole));
            if (aimed > 0) return aimed;
        }
        return PoleKind.REMOTE.height();
    }

    /**
     * 视角<b>指着的那根杆</b>的杆顶；指的不是杆则返回 {@code null}。
     *
     * <p>这是磁盘连接那一路的落点：拿磁盘时并没有「要放下的方块」，所以不能用
     * {@link #placementHead}——它算的是「瞄准方块<b>旁边那一格</b>」（放置预览的目标格），
     * 于是粒子束会偏出整整一格，看起来就像「对杆顶的判断没生效」。</p>
     *
     * <p>这里直接对着瞄准的杆取顶：任意一段 → {@link PoleResolver#resolveTop} → 顶段上表面。
     * 由于 {@code resolveTop} 已经把中段/底段归到顶段，<b>不需要知道杆是三格还是四格</b>。</p>
     */
    public static @Nullable Vec3 aimedPoleHead(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        if (minecraft.level == null) return null;
        BlockPos top = PoleResolver.resolveTop(minecraft.level, hit.getBlockPos());
        return top == null ? null : masterHead(top);
    }
}
