package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.network.SyncLinkStatsPacket;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.PoleConfig;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.api.tooltip.providers.ITooltipProvider;
import dev.dubhe.anvilcraft.util.UnitUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

    /**
     * 不是输电杆则返回 null，调用方据此决定是否改动原有 tooltip。
     *
     * <p>认杆走 {@link PoleKind#ofComponent}，不再写 {@code instanceof} 清单：
     * 之前这里只判本体的两种杆，而我们自己的两种杆方块实体直接继承
     * {@code AbstractTransmissionPoleBlockEntity}，于是**超远程杆与跨维度杆一条 tooltip 都没有**。</p>
     */
    private static PoleKey poleKeyOf(BlockEntity entity) {
        if (PoleKind.ofComponent(entity) == null) return null;
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

    /**
     * 本模组追加的行：拉电线块与锁定状态。
     *
     * <p><b>四种杆一视同仁</b>：本体那两种杆与我们的两种杆显示完全相同的内容
     * （角色、外送 / 内收、优先级、锁定）。这里不按杆型分流——「这跟杆现在在给别人送多少电」
     * 对四种杆都是同样的信息。</p>
     *
     * <p><b>没有链路时也要显示角色与优先级</b>：早期版本在电网统计缺失时整块跳过，
     * 结果一根「放好了但还没连任何线」的杆什么都不显示——玩家看到的和「这个模组没生效」一样。</p>
     */
    public static List<Component> of(BlockEntity entity) {
        PoleKey key = poleKeyOf(entity);
        if (key == null) return List.of();

        PoleConfig config = ManualLinkClientState.configOf(key);
        List<Component> lines = new ArrayList<>(6);

        // 上游 = 本杆作为从端的入边数（有几个主端在给它送电）；
        // 下游 = 本杆作为主端的出边数（它在给几个从端送电）。
        int upstream = 0;
        int downstream = 0;
        for (LinkDefinition definition : ManualLinkClientState.links()) {
            if (definition.slave().equals(key)) upstream++;
            if (definition.master().equals(key)) downstream++;
        }

        List<Component> inner = new ArrayList<>(4);
        if (upstream > 0 || downstream > 0) {
            inner.add(indent(roleLine(upstream, downstream)));

            SyncLinkStatsPacket.Entry stats = ManualLinkClientState.statsOf(key);
            if (stats != null) {
                // 外送 / 内收紧跟在角色行之后，各自独立成行，为 0 时隐藏。
                if (stats.sendNow() > 0) {
                    inner.add(indent(Component.translatable(
                        "tooltip.anvilcraft_pullwires.link.send",
                        electricity(stats.sendNow())
                    ).withStyle(ChatFormatting.GRAY)));
                }
                if (stats.recvNow() > 0) {
                    inner.add(indent(Component.translatable(
                        "tooltip.anvilcraft_pullwires.link.receive",
                        electricity(stats.recvNow())
                    ).withStyle(ChatFormatting.GRAY)));
                }
            }

            inner.add(indent(Component.translatable(
                "tooltip.anvilcraft_pullwires.link.priority",
                Component.literal(String.valueOf(config.priority())).withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY)));
        }

        // 块内一行都留不下就不显示标题——空标题比不显示更让人困惑。
        if (!inner.isEmpty()) {
            lines.add(Component.translatable("tooltip.anvilcraft_pullwires.link.header")
                .withStyle(ChatFormatting.BLUE));
            lines.addAll(inner);
        }

        // 未锁定不显示；锁定后才出现（标题级）。本模组没有任何「未锁定」提示文案。
        if (config.locked()) {
            lines.add(Component.translatable("tooltip.anvilcraft_pullwires.locked")
                .withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /**
     * 上下游计数行：{@code 2>>从端|主端>>3}。
     *
     * <p>两侧各自只在非零时出现，中间用 {@code |} 连接。箭头指向「从哪来到哪去」：
     * 左边是别人指向本杆（本杆作为从端），右边是本杆指向别人（本杆作为主端）。</p>
     */
    private static MutableComponent roleLine(int upstream, int downstream) {
        MutableComponent line = Component.empty();
        if (upstream > 0) {
            line.append(Component.translatable(
                "tooltip.anvilcraft_pullwires.link.upstream",
                Component.literal(String.valueOf(upstream)).withStyle(ChatFormatting.YELLOW)
            ));
        }
        if (upstream > 0 && downstream > 0) {
            line.append(Component.literal("|").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (downstream > 0) {
            line.append(Component.translatable(
                "tooltip.anvilcraft_pullwires.link.downstream",
                Component.literal(String.valueOf(downstream)).withStyle(ChatFormatting.YELLOW)
            ));
        }
        return line.withStyle(ChatFormatting.GRAY);
    }

    /**
     * 与本体一致：按住 shift 显示原始数值（kW），否则允许换算到 MW。
     *
     * <p>用 {@link Screen#hasShiftDown()} 而不是 {@code player.isShiftKeyDown()}：后者读的是实体的
     * 潜行状态，与本体 {@code ItemTooltipManager} 用的原始按键状态不是同一个东西，
     * 会出现「本体那半张提示变了、我们这半张没变」。</p>
     */
    private static String electricity(long amount) {
        int clamped = (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, amount));
        return UnitUtil.electricityUnit(clamped, Screen.hasShiftDown(), false);
    }

    private static Component indent(Component line) {
        return ITooltipProvider.INDENTATION.copy().append(line);
    }
}