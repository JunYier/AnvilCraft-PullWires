package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.dubhe.anvilcraft.api.power.ConnectivityChecker;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerComponentType;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;

import java.util.ArrayList;
import java.util.List;

/**
 * 把本体的「连接范围即供电范围」拆成两档。
 *
 * <p>本体 {@code IPowerComponent#getShape()} 只有一个范围（{@code range}），它同时决定组网与供电。
 * 本模组让输电杆多出一个更大的<b>自动连接范围</b>：杆与杆之间用<b>包围盒</b>在这个范围内组网，
 * 但发电机／用电器／储电元件仍然只能按原本的 {@code range}（包围盒）并网，
 * 因此扩大范围不会凭空供电。</p>
 *
 * <p>为了不改动本体逻辑，这里走本体提供的 {@code ConnectivityChecker} 扩展点：
 * 只对<b>中继元件（输电杆）</b>返回 {@code TRUE}/{@code FALSE} 给出明确结论，
 * 其余元件一律返回 {@code DEFAULT}，完全沿用本体 {@code FastCollisionConnectivityChecker} 的判定。</p>
 *
 * <p><b>范围来源</b>：{@code range}（供电范围）继续取自本体的
 * {@code machines.powerTransmitterRange} / {@code remotePowerTransmitterRange}，
 * 这样现有存档与配置项含义完全不变；本模组只新增「自动连接范围」一个配置。</p>
 *
 * <h2>跨维度杆没有任何特殊分支（V1.12.6）</h2>
 * <p>早期版本给跨维度杆加了「不参与杆间自动连接」的例外（把它的节点置为 {@code null}）。
 * 但那是<b>半套规则</b>：它对「自己主动并网」生效，对「别人靠过来的连接盒碰到它」却不生效，
 * 于是它照样会和旁边的杆并到一起，产生了大量难查的行为。
 * 现在删掉这个例外——<b>跨维度杆就是一个 {@code range = 0} 的普通电线杆</b>：
 * 它的供电框与连接盒都是 1×1×1，只有真正贴着才会并网，不需要任何按种类写的分支。</p>
 */
public class PowerLinkConnectivityChecker extends ConnectivityChecker {
    public static final PowerLinkConnectivityChecker INSTANCE = new PowerLinkConnectivityChecker();

    private PowerLinkConnectivityChecker() {
    }

    @Override
    public TriState checkInRange(PowerGrid powerGrid, IPowerComponent component) {
        // 发电机／用电器／储电元件：交回本体判定，其 getShape() 就是供电范围。
        if (component.getComponentType() != PowerComponentType.TRANSMITTER) return TriState.DEFAULT;

        List<PowerLinkRule.Box> gridSupply = new ArrayList<>();
        List<PowerLinkRule.LinkNode> gridTransmitters = new ArrayList<>();
        for (IPowerComponent other : powerGrid.getComponents()) {
            gridSupply.add(toBox(other.getShape()));
            if (other.getComponentType() == PowerComponentType.TRANSMITTER) {
                gridTransmitters.add(linkNode(other));
            }
        }

        boolean canJoin = PowerLinkRule.canTransmitterJoin(
            toBox(component.getShape()),
            gridSupply,
            linkNode(component),
            gridTransmitters
        );
        // 返回 FALSE 而不是 DEFAULT：明确接管输电杆的判定，不依赖本体 checker 的内部实现。
        return canJoin ? TriState.TRUE : TriState.FALSE;
    }

    /** 输电杆的连接节点，范围见 {@link PowerLinkRule#linkRange(int, int)}。 */
    public static PowerLinkRule.LinkNode linkNode(IPowerComponent component) {
        // 按「杆的种类」取自动连接范围，而不是全局一套。
        int range = PowerLinkRule.linkRange(
            component.getRange(),
            PoleKind.autoConnectRange(PoleKind.ofComponent(component))
        );
        Vec3 center = component.getPos().getCenter();
        return new PowerLinkRule.LinkNode(center.x, center.y, center.z, range);
    }

    private static PowerLinkRule.Box toBox(AABB box) {
        return new PowerLinkRule.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}
