package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

/**
 * Jade 的输电杆信息浮层。
 *
 * <p>直接复用方块 tooltip 的那份计算（{@link PoleTooltipLines#of}），
 * 因此「看着杆显示的角色 / 优先级 / 本拍功率」与「物品 tooltip 里的数字」永远同源——
 * 不会出现两处各算一遍、结果对不上的情况。</p>
 *
 * <p>本模组只<b>追加自己那几行</b>，不接管 Jade 的任何既有显示。</p>
 *
 * <p><b>{@link #isRequired()} 必须返回 true</b>：Jade 有「切换 / 按住 / 简洁」几种显示模式
 * （见 {@code config.jade.display_mode_*}），非 required 的提供器在「按住 / 简洁」模式下
 * 会被整体折叠进「按住 &lt;键&gt; 显示细节」那一档——于是玩家按住对应键之前，本模组的几行根本不出现，
 * 看起来就像「这个模组没生效」。标记为 required 后，这几行在<b>所有</b>显示模式下都常驻。</p>
 */
public enum PoleInfoProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockEntity entity = accessor.getBlockEntity();
        if (entity == null) return;
        List<Component> lines = PoleTooltipLines.of(entity);
        if (lines.isEmpty()) return;
        tooltip.addAll(lines);
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public ResourceLocation getUid() {
        return AnvilCraftPullWires.of("pole_info");
    }
}
