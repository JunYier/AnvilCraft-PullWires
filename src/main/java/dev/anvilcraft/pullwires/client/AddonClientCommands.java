package dev.anvilcraft.pullwires.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * 常驻连接列表的开关。
 *
 * <p>做成<b>客户端指令</b>而不是服务端指令：列表是纯客户端表现，不该受服务器 OP 限制，
 * 也不会因为服务器没装本模组就失效。</p>
 */
public final class AddonClientCommands {
    private AddonClientCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> display = Commands.literal("display")
            .executes(context -> apply(!AnvilCraftPullWiresClient.CONFIG.ui.connectionListAlwaysOnDisplay, context.getSource()))
            .then(Commands.literal("true").executes(context -> apply(true, context.getSource())))
            .then(Commands.literal("false").executes(context -> apply(false, context.getSource())));

        event.getDispatcher().register(Commands.literal("wires").then(display));
    }

    private static int apply(boolean enabled, CommandSourceStack source) {
        AnvilCraftPullWiresClient.CONFIG.ui.connectionListAlwaysOnDisplay = enabled;
        source.sendSuccess(
            () -> Component.translatable(
                enabled
                    ? "command.anvilcraft_pullwires.display.on"
                    : "command.anvilcraft_pullwires.display.off"
            ),
            false
        );
        return enabled ? 1 : 0;
    }
}