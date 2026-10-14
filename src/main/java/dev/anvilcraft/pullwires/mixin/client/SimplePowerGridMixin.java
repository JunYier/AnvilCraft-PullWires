package dev.anvilcraft.pullwires.mixin.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.line.PowerLineLayout;
import dev.anvilcraft.pullwires.power.link.PowerLinkRule;
import dev.dubhe.anvilcraft.api.power.PowerComponentInfo;
import dev.dubhe.anvilcraft.api.power.PowerComponentType;
import dev.dubhe.anvilcraft.api.power.SimplePowerGrid;
import dev.dubhe.anvilcraft.client.renderer.Line;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用「凸包 ∪ 最小生成树」替换本体在输电杆之间画 Delaunay 三角剖分的逻辑。
 *
 * <p>本体 {@code SimplePowerGrid#createTransmitterVisualLines} 会对所有输电杆做三角剖分，
 * 于是四根杆之间会多出一条对角线（玩家看到的「X」）、密集杆阵会退化成一整片三角网。
 * 这里在方法入口接管，改为求解最简闭合图形。</p>
 *
 * <p>{@code require = 0} 是刻意为之：如果本体后续改动了该方法名或签名，注入会静默失效、
 * 退回本体原有表现，而不是让游戏启动失败。</p>
 */
@Mixin(SimplePowerGrid.class)
public abstract class SimplePowerGridMixin {
    /**
     * 只需要写，不需要读 {@code powerComponentInfoList}——它是 final 字段，
     * 用本体生成的公开 getter 取值可以完全避开对 final 字段的 {@code @Shadow}。
     */
    @Shadow
    private Set<Line> powerTransmitterLines;

    @Inject(method = "createTransmitterVisualLines", at = @At("HEAD"), cancellable = true, require = 0)
    private void pullwires$minimalClosedLayout(CallbackInfo callback) {
        // 保留原方法的短路：已经算过就不重算。
        if (!this.powerTransmitterLines.isEmpty()) return;

        List<PowerComponentInfo> transmitters = new ArrayList<>();
        for (PowerComponentInfo info : ((SimplePowerGrid) (Object) this).getPowerComponentInfoList()) {
            if (info.type() == PowerComponentType.TRANSMITTER) transmitters.add(info);
        }

        int count = transmitters.size();
        if (count <= 2) {
            // 与本体保持一致：0/1 根不画线；恰好 2 根无条件连一条。
            this.powerTransmitterLines = count == 2
                ? Set.of(line(transmitters.get(0), transmitters.get(1)))
                : Set.of();
            callback.cancel();
            return;
        }

        // 同步过来的 range 是「供电范围」，而杆与杆之间的组网靠的是更大的「连接范围」。
        // 视觉过滤必须用连接范围 + 直线距离，否则会出现「电网连上了、线却不画」。
        int configuredLinkRange = AnvilCraftPullWires.CONFIG.transmission.autoConnectRange;
        List<PowerLineLayout.Point2> projected = new ArrayList<>(count);
        List<PowerLinkRule.LinkNode> nodes = new ArrayList<>(count);
        for (PowerComponentInfo info : transmitters) {
            Vec3 center = info.pos().getCenter();
            // 与本体一致的微小偏移：把垂直堆叠的杆在平面上分开，避免产生零长边。
            double offset = (center.y - 1024) / 2048;
            projected.add(new PowerLineLayout.Point2(center.x + offset, center.z + offset));
            nodes.add(new PowerLinkRule.LinkNode(
                center.x, center.y, center.z, PowerLinkRule.linkRange(info.range(), configuredLinkRange)
            ));
        }

        List<PowerLineLayout.Edge> edges = PowerLineLayout.layout(
            projected, (first, second) -> PowerLinkRule.linked(nodes.get(first), nodes.get(second))
        );

        Set<Line> lines = new HashSet<>();
        for (PowerLineLayout.Edge edge : edges) {
            lines.add(line(transmitters.get(edge.a()), transmitters.get(edge.b())));
        }
        this.powerTransmitterLines = lines;
        callback.cancel();
    }

    private static Line line(PowerComponentInfo from, PowerComponentInfo to) {
        Vec3 start = from.pos().getCenter();
        Vec3 end = to.pos().getCenter();
        return new Line(start, end, (float) start.distanceTo(end));
    }
}
