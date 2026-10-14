package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.util.PoleResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * 锁定状态的判定入口，供两个杆型方块的 Mixin 共用。
 *
 * <p>锁定的语义是<b>忽略外部红石信号造成的 {@code SWITCH} 切换</b>：
 * 红石既不能开关该杆，也不能借「开关重启」触发链路重连。</p>
 *
 * <p>这个方法会在 {@@code TransmissionPoleBlock#update} 的入口被调用，而 {@code update}
 * 由 {@code neighborChanged} 与 {@code onPlace} 触发——也就是说<b>每次邻居变化都会走一遍</b>。
 * 因此这里的第一层判断是 {@code isClientSide} 快速返回，第二层是一次哈希查找，
 * 只有真的是输电杆、且服务端、且该坐标命中锁定索引时才会继续。</p>
 */
public final class PoleRedstoneLock {
    private PoleRedstoneLock() {
    }

    /** 该坐标所属的输电杆是否处于锁定状态（忽略红石）。 */
    public static boolean isLocked(Level level, BlockPos pos) {
        if (level.isClientSide) return false;
        MinecraftServer server = level.getServer();
        if (server == null) return false;

        // 玩家可能改的是任意一段；链路数据以顶段为键，这里顺带确认了「这确实是输电杆」。
        BlockPos top = PoleResolver.resolveTop(level, pos);
        if (top == null) return false;
        return WireLinkRegistry.get(server).isLocked(level.dimension(), top);
    }
}
