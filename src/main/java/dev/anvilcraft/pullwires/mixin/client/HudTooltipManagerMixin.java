package dev.anvilcraft.pullwires.mixin.client;

import dev.anvilcraft.pullwires.client.PoleTooltipLines;
import dev.dubhe.anvilcraft.api.tooltip.HudTooltipManager;
import dev.dubhe.anvilcraft.api.tooltip.providers.ITooltipProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * 在铁砧锤 hud tooltip 里追加输电杆的补充信息。
 *
 * <h2>为什么不注册一个自己的 provider</h2>
 * <p>本体的 {@code HudTooltipManager} 是「<b>优先级数字最小者胜出</b>」，并且只取一个提供器
 * （{@code determineBlockEntityTooltipProvider} 里的 {@code min(priority)}）。
 * 也就是说注册提供器不是「追加」而是「<b>替换</b>」：本体自己的
 * {@code PowerComponentTooltipProvider}（priority = 0，且 accepts 一切 IPowerComponent，
 * 输电杆正好属于这一类）会被顶掉，玩家就看不到电网总量那几行了。</p>
 *
 * <p>这里改成在提供器返回结果上追加，因此<b>谁胜出都能挂在其下面</b>——
 * 本体后续换提供器、或者别的附属模组提供了优先级更低的提供器，我们的信息都不会消失。</p>
 *
 * <p>{@code require = 0}：本体改动该方法时静默失效，只是少了几行提示。</p>
 */
@Mixin(HudTooltipManager.class)
public abstract class HudTooltipManagerMixin {
    @Redirect(
        method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;"
                 + "Lnet/minecraft/world/level/block/entity/BlockEntity;FII)V",
        at = @At(
            value = "INVOKE",
            target = "Ldev/dubhe/anvilcraft/api/tooltip/providers/ITooltipProvider$BlockEntityTooltipProvider;"
                     + "tooltip(Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"
        ),
        require = 0
    )
    private List<Component> pullwires$appendPoleLines(
        ITooltipProvider.BlockEntityTooltipProvider provider, BlockEntity entity
    ) {
        List<Component> base = provider.tooltip(entity);
        List<Component> extra = PoleTooltipLines.of(entity);
        if (extra.isEmpty()) return base;
        List<Component> merged = new ArrayList<>(
            base == null ? List.of() : base
        );
        merged.addAll(extra);
        return merged;
    }
}
