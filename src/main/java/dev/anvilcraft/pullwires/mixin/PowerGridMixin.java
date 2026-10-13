package dev.anvilcraft.pullwires.mixin;

import dev.anvilcraft.pullwires.power.link.PowerLinkConnectivityChecker;
import dev.anvilcraft.pullwires.power.link.PowerLinkRule;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerComponentType;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 让电网内部的邻接图也认识「自动连接范围」。
 *
 * <p>本体 {@code PowerGrid#connect} 只用 {@code getShape()}（供电框）建立 {@code connections} 邻接图，
 * 而本模组允许两根输电杆靠更大的自动连接范围并网。若不修补，这类杆在邻接图里是个孤立点，
 * 一旦电网发生拆分裂解，{@code findConnectedComponents} 会把它单独分出去，连接就被无声破坏。</p>
 *
 * <p>这里采用<b>只增不减</b>的做法：在 {@code connect} 结束后，为输电杆补上连接范围相邻的边，
 * 并且双向登记。本体原有的判定与拆分流程完全不动，只是邻接图变得更完整。</p>
 *
 * <p>{@code require = 0}：若本体改动了该方法，注入静默失效并退回本体行为（连接范围只影响并网判定、
 * 不影响拆分），而不是让游戏启动失败。</p>
 */
@Mixin(PowerGrid.class)
public abstract class PowerGridMixin {
    @Shadow
    private Map<IPowerComponent, Set<IPowerComponent>> connections;

    @Inject(method = "connect", at = @At("TAIL"), require = 0)
    private void pullwires$addLinkRangeNeighbours(IPowerComponent component, CallbackInfo callback) {
        if (component.getComponentType() != PowerComponentType.TRANSMITTER) return;
        Set<IPowerComponent> neighbours = this.connections.get(component);
        if (neighbours == null) return;

        PowerLinkRule.Box candidate = PowerLinkConnectivityChecker.linkBox(component);
        for (IPowerComponent other : ((PowerGrid) (Object) this).getComponents()) {
            if (other == component) continue;
            if (other.getComponentType() != PowerComponentType.TRANSMITTER) continue;
            if (!candidate.intersects(PowerLinkConnectivityChecker.linkBox(other))) continue;
            neighbours.add(other);
            this.connections.computeIfAbsent(other, ignored -> new HashSet<>()).add(component);
        }
    }
}
