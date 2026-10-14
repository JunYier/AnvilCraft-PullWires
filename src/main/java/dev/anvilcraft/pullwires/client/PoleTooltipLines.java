package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.network.SyncLinkStatsPacket;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.PoleConfig;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.api.tooltip.providers.ITooltipProvider;
import dev.dubhe.anvilcraft.block.entity.RemoteTransmissionPoleBlockEntity;
import dev.dubhe.anvilcraft.block.entity.TransmissionPoleBlockEntity;
import dev.dubhe.anvilcraft.util.UnitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 输电杆的补充 tooltip 行。
 *
 * <h2>版面（三个同级标题）</h2>
 * <pre>
 * 电网统计：
 *   总耗电量：80+40 kW
 *   总发电量：160+40 kW
 * 拉电线（主端）：
 *   发送（接收）电量：16 kW
 *   总转移电量：40 kW
 *   优先级 7
 * 未锁定
 * </pre>
 *
 * <p>「电网统计」是本体原有的块，我们只在其两行后追加本模组的转移量；
 * 「拉电线」与锁定状态是本模组新增的，且锁定状态是<b>标题级</b>、无链路时也始终可见。</p>
 */
public final class PoleTooltipLines {
    private static final String GRID_CONSUMED = "tooltip.anvilcraft.grid_information.total_consumed";
    private static final String GRID_GENERATED = "tooltip.anvilcraft.grid_information.total_generated";

    private PoleTooltipLines() {
    }

    /** 不是输电杆则返回 null，调用方据此决定是否改动原有 tooltip。 */
    private static PoleKey poleKeyOf(BlockEntity entity) {
        if (!(entity instanceof TransmissionPoleBlockEntity) && !(entity instanceof RemoteTransmissionPoleBlockEntity)) {
            return null;
        }
        Level level = entity.getLevel();
        if (level == null) return null;
        BlockPos top = PoleResolver.resolveTop(level, entity.getBlockPos());
        if (top == null) return null;
        return PoleKey.of(level, top);
    }

    /**
     * 把本体「电网统计」里的总耗电 / 总发电两行改写成带转移量的形式。
     *
     * <p>不改数字本身，只在其后追加 +xx。<b>追加量为 0 时原样返回</b>，
     * 这样纯本体电网的观感与未装本模组时完全一致。</p>
     */
    public static List<Component> rewriteGridLines(List<Component> base, BlockEntity entity) {
        PoleKey key = poleKeyOf(entity);
        if (key == null) return base;
        SyncLinkStatsPacket.Entry stats = ManualLinkClientState.statsOf(key);
        if (stats == null || (stats.gridOut() <= 0 && stats.gridIn() <= 0)) return base;

        List<Component> result = new ArrayList<>(base.size());
        for (Component line : base) {
            if (!(line.getContents() instanceof TranslatableContents contents)) {
                result.add(line);
                continue;
            }
            boolean consumed = GRID_CONSUMED.equals(contents.getKey());
            boolean generated = GRID_GENERATED.equals(contents.getKey());
            int extra = consumed ? stats.gridOut() : generated ? stats.gridIn() : 0;
            Object[] args = contents.getArgs();
            if (extra <= 0 || args.length == 0) {
                result.add(line);
                continue;
            }
            String rendered = args[0] instanceof Component component
                ? component.getString()
                : String.valueOf(args[0]);
            // 本体渲染出来是 "80 kW" 这种「数字 单位」形式，拆开才能拼成 "80+40 kW"。
            int space = rendered.lastIndexOf(' ');
            String number = space < 0 ? rendered : rendered.substring(0, space);
            String unit = space < 0 ? "" : rendered.substring(space + 1);
            String extraText = formatExtra(extra, unit);
            if (extraText == null) {
                result.add(line);
                continue;
            }
            result.add(Component.translatable(
                consumed
                    ? "tooltip.anvilcraft_pullwires.grid_information.total_consumed"
                    : "tooltip.anvilcraft_pullwires.grid_information.total_generated",
                Component.literal(number).withStyle(ChatFormatting.GOLD),
                Component.literal(extraText).withStyle(ChatFormatting.GREEN)
            ).withStyle(ChatFormatting.GRAY));
        }
        return result;
    }

    /** 追加量的文字；无法安全换算（如无限电力）时返回 null 表示放弃改写。 */
    private static String formatExtra(int extra, String unit) {
        if (UnitUtil.INFINITE_POWER.equals(unit)) return null;
        return switch (unit) {
            case "MW" -> String.format(Locale.ROOT, "%.2f MW", extra / 1000.0);
            case "FE", "kFE", "MFE" -> null;
            default -> extra + (unit.isEmpty() ? "" : " " + unit);
        };
    }

    /** 本模组追加的行：拉电线块与锁定状态。 */
    public static List<Component> of(BlockEntity entity) {
        PoleKey key = poleKeyOf(entity);
        if (key == null) return List.of();

        PoleConfig config = ManualLinkClientState.configOf(key);
        List<Component> lines = new ArrayList<>(5);

        SyncLinkStatsPacket.Entry stats = ManualLinkClientState.statsOf(key);
        if (stats != null) {
            boolean asMaster = false;
            boolean asSlave = false;
            for (LinkDefinition definition : ManualLinkClientState.links()) {
                if (definition.master().equals(key)) asMaster = true;
                if (definition.slave().equals(key)) asSlave = true;
            }
            if (asMaster || asSlave) {
                lines.add(Component.translatable(titleKey(asMaster, asSlave))
                    .withStyle(ChatFormatting.BLUE));
                lines.add(indent(amountLine(stats, asMaster, asSlave)));
                lines.add(indent(Component.translatable(
                    "tooltip.anvilcraft_pullwires.link.total",
                    electricity(stats.sendTotal() + stats.recvTotal())
                ).withStyle(ChatFormatting.GRAY)));
                lines.add(indent(Component.translatable(
                    "tooltip.anvilcraft_pullwires.link.priority",
                    Component.literal(String.valueOf(config.priority())).withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GRAY)));
            }
        }

        // 锁定状态是标题级，且无论有没有链路都始终可见。
        lines.add(Component.translatable(config.locked()
                ? "tooltip.anvilcraft_pullwires.locked"
                : "tooltip.anvilcraft_pullwires.unlocked")
            .withStyle(config.locked() ? ChatFormatting.RED : ChatFormatting.GRAY));
        return lines;
    }

    private static String titleKey(boolean asMaster, boolean asSlave) {
        if (asMaster && asSlave) return "tooltip.anvilcraft_pullwires.link.both";
        return asMaster
            ? "tooltip.anvilcraft_pullwires.link.master"
            : "tooltip.anvilcraft_pullwires.link.slave";
    }

    private static Component amountLine(SyncLinkStatsPacket.Entry stats, boolean asMaster, boolean asSlave) {
        if (asMaster && asSlave) {
            return Component.translatable(
                "tooltip.anvilcraft_pullwires.link.send_receive",
                electricity(stats.sendNow()),
                electricity(stats.recvNow())
            ).withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable(
            asMaster
                ? "tooltip.anvilcraft_pullwires.link.send"
                : "tooltip.anvilcraft_pullwires.link.receive",
            electricity(asMaster ? stats.sendNow() : stats.recvNow())
        ).withStyle(ChatFormatting.GRAY);
    }

    /** 与本体一致：按住 shift 显示原始数值（kW），否则允许换算到 MW。 */
    private static String electricity(long amount) {
        boolean original = Minecraft.getInstance().player != null
                           && Minecraft.getInstance().player.isShiftKeyDown();
        int clamped = (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, amount));
        return UnitUtil.electricityUnit(clamped, original, false);
    }

    private static Component indent(Component line) {
        return ITooltipProvider.INDENTATION.copy().append(line);
    }
}