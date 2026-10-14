package dev.anvilcraft.pullwires.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 手动连接的取点：主端头部、放置预览头部、以及两者之间的距离。
 *
 * <p>粒子束与 HUD 都用这里，保证「看到的距离」就是「用来判定的距离」。</p>
 */
public final class ManualLinkAim {
    /** 主端头部：顶段方块的上表面中心。 */
    private static final double MASTER_HEAD_OFFSET = 1.0;

    /**
     * 放置预览轮廓的头部高度偏移。
     *
     * <p>1 是本格上表面；额外再加 2 格是实测对齐本体碰撞箱勾勒顶部所需的高度——
     * 粒子束要刚好落在预览虚影的顶端。</p>
     */
    private static final double PLACEMENT_HEAD_OFFSET = 3.0;

    private ManualLinkAim() {
    }

    public static Vec3 headOf(BlockPos pos, double heightOffset) {
        return Vec3.atBottomCenterOf(pos).add(0.0, heightOffset, 0.0);
    }

    public static Vec3 masterHead(BlockPos master) {
        return headOf(master, MASTER_HEAD_OFFSET);
    }

    /**
     * 方块放置预览的头部。
     *
     * <p>用当前帧的 {@code hitResult} 推算出「这一下会放在哪一格」，与本体画碰撞箱勾勒用的是同一个依据。
     * 没瞄准任何方块时没有预览轮廓，自然也没有头部。</p>
     */
    public static @Nullable Vec3 placementHead(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) return null;
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos placement = hit.getBlockPos().relative(hit.getDirection());
        return headOf(placement, PLACEMENT_HEAD_OFFSET);
    }

    /**
     * 当前应当显示、也用于判定的距离。
     *
     * <p>有放置预览时量到预览头部（与最终放置校验同一个基准）；没有预览时退化为玩家眼睛到主端头部，
     * 这样 HUD 在瞄准空气时也有数可读。</p>
     */
    public static double distance(Minecraft minecraft, BlockPos master) {
        Vec3 from = masterHead(master);
        Vec3 to = placementHead(minecraft);
        if (to != null) return from.distanceTo(to);
        if (minecraft.player == null) return 0.0;
        return from.distanceTo(minecraft.player.getEyePosition());
    }
}
