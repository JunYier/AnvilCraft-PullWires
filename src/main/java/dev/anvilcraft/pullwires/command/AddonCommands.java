package dev.anvilcraft.pullwires.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.power.link.LinkDefinition;
import dev.anvilcraft.pullwires.power.link.LinkState;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.anvilcraft.pullwires.power.link.WireLinkRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 调试与运维命令。
 *
 * <h2>为什么需要它</h2>
 * <p>链路只能由手动连接（M5）或磁盘连接（M10）建立，在那之前链路互联的结算没有任何数据可算。
 * 这条命令让链路互联能够被独立验证：「两座电网互联后，缺电那座的用电器应当开始工作」。
 * 排查线上问题时它同样有用——能直接看到每条链路当前处于哪个状态。</p>
 *
 * <p>权限等级 2（OP），因为它可以凭空把两座不相邻的电网接起来。</p>
 *
 * <h2>状态图标</h2>
 * <p>图标是 {@code assets/anvilcraft_pullwires/font/status_icons.json} 里用 bitmap 字形映射的原版物品贴图，
 * 把鼠标停在图标上会显示状态名称。这样既能在聊天栏里内联显示图形，又不需要额外贴图资源。</p>
 */
public final class AddonCommands {
    private static final ResourceLocation ICON_FONT = AnvilCraftPullWires.of("status_icons");

    private AddonCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("wires")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("link")
                    .then(Commands.argument("posA", BlockPosArgument.blockPos())
                        .then(Commands.argument("posB", BlockPosArgument.blockPos())
                            .executes(AddonCommands::link))))
                .then(Commands.literal("unlink")
                    .then(Commands.argument("posA", BlockPosArgument.blockPos())
                        .then(Commands.argument("posB", BlockPosArgument.blockPos())
                            .executes(AddonCommands::unlink))))
                .then(Commands.literal("list").executes(AddonCommands::list))
                // 清空是破坏性操作，要求显式 confirm，避免手滑。
            .then(Commands.literal("clear")
                .executes(context -> {
                    send(
                        context.getSource(),
                        prefix().append(translatable("clear.confirm_hint", ChatFormatting.YELLOW))
                    );
                    return 0;
                })
                .then(Commands.literal("confirm").executes(AddonCommands::clear)))
        );
    }

    private static int link(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos topA = resolvePoleTop(level, BlockPosArgument.getLoadedBlockPos(context, "posA"));
        BlockPos topB = resolvePoleTop(level, BlockPosArgument.getLoadedBlockPos(context, "posB"));
        if (topA == null || topB == null) {
            send(source, prefix().append(translatable("error.no_pole", ChatFormatting.RED)));
            return 0;
        }
        if (topA.equals(topB)) {
            send(source, prefix().append(translatable("error.same_pole", ChatFormatting.RED)));
            return 0;
        }

        WireLinkRegistry registry = WireLinkRegistry.get(source.getServer());
        PoleKey master = PoleKey.of(level, topA);
        PoleKey slave = PoleKey.of(level, topB);
        // 同向重复会被拒绝（返回 null）；反向允许，因此这里必须区分。
        if (registry.createLink(master, slave) == null) {
            send(source, prefix().append(translatable("error.duplicate_direction", ChatFormatting.RED)));
            return 0;
        }
        AddonNetwork.broadcastRegistry(source.getServer());

        send(source, prefix().append(translatable("link.created", ChatFormatting.WHITE)));
        send(source, Component.translatable(
            "command.anvilcraft_pullwires.link.line",
            poleLabel(master),
            priorityLabel(registry.config(master).priority()),
            poleLabel(slave)
        ));
        return 1;
    }

    private static int unlink(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos topA = resolvePoleTop(level, BlockPosArgument.getLoadedBlockPos(context, "posA"));
        BlockPos topB = resolvePoleTop(level, BlockPosArgument.getLoadedBlockPos(context, "posB"));
        if (topA == null || topB == null) {
            send(source, prefix().append(translatable("error.no_pole", ChatFormatting.RED)));
            return 0;
        }

        WireLinkRegistry registry = WireLinkRegistry.get(source.getServer());
        PoleKey first = PoleKey.of(level, topA);
        PoleKey second = PoleKey.of(level, topB);
        List<UUID> affected = new ArrayList<>();
        for (LinkDefinition definition : registry.links()) {
            boolean direct = definition.master().equals(first) && definition.slave().equals(second);
            boolean reversed = definition.master().equals(second) && definition.slave().equals(first);
            if (direct || reversed) affected.add(definition.id());
        }
        for (UUID id : affected) registry.removeLink(id);
        if (!affected.isEmpty()) AddonNetwork.broadcastRegistry(source.getServer());

        int removed = affected.size();
        send(source, prefix().append(removed == 0
            ? translatable("unlink.none", ChatFormatting.GRAY)
            : translatable("unlink.removed", ChatFormatting.WHITE, number(removed))));
        return removed;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        WireLinkRegistry registry = WireLinkRegistry.get(source.getServer());
        List<LinkDefinition> links = List.copyOf(registry.links());
        if (links.isEmpty()) {
            send(source, prefix().append(translatable("list.empty", ChatFormatting.GRAY)));
            return 0;
        }
        send(source, prefix().append(translatable("list.header", ChatFormatting.WHITE, number(links.size()))));
        for (LinkDefinition definition : links) {
            PoleKey master = definition.master();
            send(source, Component.translatable(
                "command.anvilcraft_pullwires.list.line",
                idLabel(definition.id()),
                poleLabel(master),
                priorityLabel(registry.config(master).priority()),
                stateIcon(registry.stateOf(definition.id())),
                poleLabel(definition.slave())
            ));
        }
        return links.size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        WireLinkRegistry registry = WireLinkRegistry.get(source.getServer());
        List<UUID> affected = registry.links().stream().map(LinkDefinition::id).toList();
        for (UUID id : affected) registry.removeLink(id);
        if (!affected.isEmpty()) AddonNetwork.broadcastRegistry(source.getServer());
        send(source, prefix().append(translatable("clear.done", ChatFormatting.WHITE, number(affected.size()))));
        return affected.size();
    }

    // ------------------------------------------------------------------ 文案拼装

    private static MutableComponent prefix() {
        return Component.translatable("command.anvilcraft_pullwires.prefix").withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent translatable(String key, ChatFormatting color, Object... args) {
        return Component.translatable("command.anvilcraft_pullwires." + key, args).withStyle(color);
    }

    private static MutableComponent number(int value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.AQUA);
    }

    /** 杆标签：{@code 主世界[12, 64, -30]}，维度名金色、坐标灰色。 */
    private static MutableComponent poleLabel(PoleKey key) {
        ResourceLocation dimension = key.dimension().location();
        BlockPos pos = key.pos();
        MutableComponent name = Component
            .translatableWithFallback("dimension." + dimension.toLanguageKey(), dimension.toString())
            .withStyle(ChatFormatting.GOLD);
        MutableComponent coords = Component
            .translatable(
                "command.anvilcraft_pullwires.coords",
                Component.literal(String.valueOf(pos.getX())),
                Component.literal(String.valueOf(pos.getY())),
                Component.literal(String.valueOf(pos.getZ()))
            )
            .withStyle(ChatFormatting.GRAY);
        return Component
            .translatable("command.anvilcraft_pullwires.pole", name, coords)
            .withStyle(ChatFormatting.DARK_GRAY);
    }

    private static MutableComponent priorityLabel(int priority) {
        return Component.literal(String.valueOf(priority)).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent idLabel(UUID id) {
        return Component.literal(id.toString().substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** 状态图标：自定义 bitmap 字体里的一个字形，颜色随状态，悬停显示状态名称。 */
    private static MutableComponent stateIcon(LinkState state) {
        return Component.literal(String.valueOf(glyph(state))).withStyle(Style.EMPTY
            .withFont(ICON_FONT)
            .withColor(stateColor(state))
            .withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                Component.translatable("command.anvilcraft_pullwires.state." + state.name().toLowerCase(Locale.ROOT))
            )));
    }

    private static char glyph(LinkState state) {
        return switch (state) {
            case ACTIVE -> '\uE000';
            case INACTIVE -> '\uE001';
            case SUSPENDED -> '\uE002';
            case PENDING -> '\uE003';
        };
    }

    private static ChatFormatting stateColor(LinkState state) {
        return switch (state) {
            case ACTIVE -> ChatFormatting.GREEN;
            case INACTIVE -> ChatFormatting.GRAY;
            case SUSPENDED -> ChatFormatting.RED;
            case PENDING -> ChatFormatting.GOLD;
        };
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 由任意一段的坐标解析出该杆的<b>顶段</b>坐标。
     *
     * <p>输电杆只有 {@code HALF == TOP} 那一段的方块实体才是中继元件，链路必须以顶段坐标登记，
     * 否则结算时按坐标匹配电网元件会找不到它。</p>
     *
     * <p>认杆走 {@link PoleKind#isHalf}：这里原本又抄了一份「本体那两个方块」的清单，
     * 于是 {@code /wires link} 根本找不到超远程杆与跨维度杆。</p>
     */
    private static @Nullable BlockPos resolvePoleTop(ServerLevel level, BlockPos pos) {
        for (int dy = -3; dy <= 3; dy++) {
            BlockPos candidate = pos.offset(0, dy, 0);
            if (PoleKind.isHalf(level.getBlockState(candidate), true)) return candidate;
        }
        return null;
    }
}
