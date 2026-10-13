package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 客户端侧的链路镜像。
 *
 * <p>服务端权威：待连接主端由 {@link dev.anvilcraft.pullwires.network.SyncPendingLinkPacket} 下发，
 * 链路定义由 {@link dev.anvilcraft.pullwires.network.SyncManualLinksPacket} 下发。
 * 客户端只读它，用来画粒子束与手动连接的电力线。</p>
 */
public final class ManualLinkClientState {
    private static @Nullable BlockPos pendingMaster;
    private static List<LinkDefinition> links = List.of();

    private ManualLinkClientState() {
    }

    /** 当前待连接的主端顶部坐标；没有待连接状态时为 {@code null}。 */
    public static @Nullable BlockPos pendingMaster() {
        return pendingMaster;
    }

    public static void setPendingMaster(@Nullable BlockPos pos) {
        pendingMaster = pos == null ? null : pos.immutable();
    }

    public static List<LinkDefinition> links() {
        return links;
    }

    public static void setLinks(List<LinkDefinition> value) {
        links = List.copyOf(value);
    }

    /** 断开连接时清空，避免把上一条线的信息带到下一个存档。 */
    public static void clear() {
        pendingMaster = null;
        links = List.of();
    }
}
