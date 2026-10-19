package dev.anvilcraft.pullwires.util;

import dev.anvilcraft.pullwires.power.link.PoleKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/**
 * 把输电杆的任意一段解析成<b>顶段</b>或<b>底段</b>坐标。
 *
 * <p>输电杆只有 {@code HALF == TOP} 那一段的方块实体才是中继元件
 * （其余段 {@code getComponentType()} 返回 {@code INVALID}），
 * 而链路的 {@code PoleKey} 必须以元件坐标登记，否则结算时按坐标匹配电网元件会找不到它。</p>
 *
 * <h2>认杆只有一条路</h2>
 * <p>本类的三个方法全部走 {@link PoleKind#isHalf}，不再各自维护「本体那两个方块」的硬编码清单。
 * 之前那套写法在新增杆型时漏改过四次（服务端 isPoleItem、客户端手持判定、isHalf、resolveTop），
 * 每次只改到自己碰到的那一处。</p>
 */
public final class PoleResolver {
    private PoleResolver() {
    }

    /** 扫描上下各三段，找离 {@code pos} 最近的指定半段。 */
    private static @Nullable BlockPos resolve(BlockGetter level, BlockPos pos, boolean top) {
        for (int dy = -3; dy <= 3; dy++) {
            BlockPos candidate = pos.offset(0, dy, 0);
            if (PoleKind.isHalf(level.getBlockState(candidate), top)) return candidate;
        }
        return null;
    }

    /** 任意一段 → 顶段坐标。 */
    public static @Nullable BlockPos resolveTop(BlockGetter level, BlockPos pos) {
        return resolve(level, pos, true);
    }

    /**
     * 任意一段 → 底段坐标。
     *
     * <p>本体的 {@code update} 只在底段执行（其余段直接返回），所以「解锁后强制重新评估一次红石状态」
     * 必须以底段为入口触发才有意义。</p>
     */
    public static @Nullable BlockPos resolveBottom(BlockGetter level, BlockPos pos) {
        return resolve(level, pos, false);
    }

    /** 该坐标是不是本模组认得的电线杆的任意一段。 */
    public static boolean isPole(BlockGetter level, BlockPos pos) {
        return PoleKind.of(level.getBlockState(pos)) != null;
    }
}