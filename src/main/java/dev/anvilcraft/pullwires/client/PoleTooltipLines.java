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

/**
 * 输电杆的补充 tooltip。
 *
 * <h2>版面</h2>
 * <pre>
 * 电网统计：
 *   总耗电量：80 kW        ← 本体原样，一个字都不改
 *   总发电量：160 kW       ← 本体原样
 *   总交换电量：40 kW      ← 本模组插入，与「总发电量」同级
 * 拉电线（主端）：
 *   外送电量：16 kW
 *   内收电量：8 kW         ← 为 0 时整行隐藏
 *   优先级：7
 * 锁定                      ← 仅锁定时出现
 * </pre>
 *
 * <h2>为什么全是「功率」而不是「累计量」</h2>
 * <p>早期版本显示过「总转移电量」，那是个只增不减的累计值，玩家看到的数字会一直上涨，
 * 无法回答「这根杆现在往外送多少」。现在所有数值都是<b>本拍功率</b>（kW），
 * 与本体自己的电网统计口径一致。</p>
 */
public final class PoleTooltipLines {
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
     * 在本体「电网统计」块里插入「总交换电量」。
     *
     * <p><b>不修改本体任何一行</b>——早期版本往「总耗电量 / 总发电量」后面拼 {@code +40}，
     * 那是在别人的字符串上做文章；现在改成在「总发电量」之后另起一行，与它同级。</p>
     *
     * <p>交换电量为 0（该电网没有对外互联）时整行不插入，纯本体电网的观感不变。</p>
     */
    public static List<Component> augmentGridLines(List<Component> base, BlockEntity entity) {
        PoleKey key = poleKeyOf(entity);
        if (key == null) return base;
        SyncLinkStatsPacket.Entry stats = ManualLinkClientState.statsOf(key);
        if (stats == null) return base;
        int exchange = stats.gridOut() + stats.gridIn();
        if (exchange <= 0) return base;

        List<Component> result = new ArrayList<>(base.size() + 1);
        for (Component line : base) {
            result.add(line);
            // 紧跟「总发电量」之后插入，与它同一视觉层级。
            if (line.getContents() instanceof TranslatableContents contents
                && GRID_GENERATED.equals(contents.getKey())) {
                result.add(indent(Component.translatable(
                    "tooltip.anvilcraft_pullwires.grid_information.exchange",
                    electricity(exchange)
                ).withStyle(ChatFormatting.GRAY)));
            }
        }
        return result;
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
                // 外送 / 内收各自独立成行，为 0 时隐藏——避免一堆「0 kW」把版面撑满。
                if (stats.sendNow() > 0) {
                    lines.add(indent(Component.translatable(
                        "tooltip.anvilcraft_pullwires.link.send",
                        electricity(stats.sendNow())
                    ).withStyle(ChatFormatting.GRAY)));
                }
                if (stats.recvNow() > 0) {
                    lines.add(indent(Component.translatable(
                        "tooltip.anvilcraft_pullwires.link.receive",
                        electricity(stats.recvNow())
                    ).withStyle(ChatFormatting.GRAY)));
                }
                lines.add(indent(Component.translatable(
                    "tooltip.anvilcraft_pullwires.link.priority",
                    Component.literal(String.valueOf(config.priority())).withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GRAY)));
            }
        }

        // 未锁定不显示；锁定后才出现（标题级）。
        if (config.locked()) {
            lines.add(Component.translatable("tooltip.anvilcraft_pullwires.locked")
                .withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private static String titleKey(boolean asMaster, boolean asSlave) {
        if (asMaster && asSlave) return "tooltip.anvilcraft_pullwires.link.both";
        return asMaster
            ? "tooltip.anvilcraft_pullwires.link.master"
            : "tooltip.anvilcraft_pullwires.link.slave";
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