package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.power.link.PoleConfig;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.api.tooltip.providers.ITooltipProvider;
import dev.dubhe.anvilcraft.block.entity.RemoteTransmissionPoleBlockEntity;
import dev.dubhe.anvilcraft.block.entity.TransmissionPoleBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 输电杆的补充 tooltip 行。
 *
 * <p>附加在本体 hud tooltip 的已有内容<b>下面</b>，复用本体的缩进与配色约定
 * （{@link ITooltipProvider#INDENTATION}、标题蓝、正文灰），这样与本体条目在视觉上是连续的一段。</p>
 */
public final class PoleTooltipLines {
    private PoleTooltipLines() {
    }

    /** 不是输电杆则返回空列表，调用方据此决定是否改动原有 tooltip。 */
    public static List<Component> of(BlockEntity entity) {
        if (!(entity instanceof TransmissionPoleBlockEntity) && !(entity instanceof RemoteTransmissionPoleBlockEntity)) {
            return List.of();
        }
        Level level = entity.getLevel();
        if (level == null) return List.of();

        // 玩家可能看着任意一段，链路数据以顶段为键。
        BlockPos top = PoleResolver.resolveTop(level, entity.getBlockPos());
        if (top == null) return List.of();

        PoleKey key = PoleKey.of(level, top);
        PoleConfig config = ManualLinkClientState.configOf(key);

        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.translatable("tooltip.anvilcraft_pullwires.title")
            .withStyle(ChatFormatting.BLUE));

        lines.add(indent(Component.translatable(
            "tooltip.anvilcraft_pullwires.priority",
            Component.literal(String.valueOf(config.priority())).withStyle(ChatFormatting.YELLOW),
            Component.translatable(config.locked()
                    ? "tooltip.anvilcraft_pullwires.locked"
                    : "tooltip.anvilcraft_pullwires.unlocked")
                .withStyle(config.locked() ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)
        ).withStyle(ChatFormatting.GRAY)));

        BlockPos pending = ManualLinkClientState.pendingMaster();
        if (pending != null && pending.equals(top)) {
            // 具体距离由 Boss 条显示，这里只标记「就是这根」。
            lines.add(indent(Component.translatable("tooltip.anvilcraft_pullwires.pending_master")
                .withStyle(ChatFormatting.GOLD)));
        }
        return lines;
    }

    private static Component indent(Component line) {
        return ITooltipProvider.INDENTATION.copy().append(line);
    }
}
