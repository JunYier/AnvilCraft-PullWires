package dev.anvilcraft.pullwires.data.lang;

import dev.anvilcraft.lib.v2.config.ConfigData;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumLangProvider;
import dev.anvilcraft.pullwires.AddonConfig;
import dev.anvilcraft.pullwires.client.AddonClientConfig;

public class AddonLangHandler {

    /**
     * 语言文件初始化。
     *
     * <p>这里只生成 {@code en_us}（以及由它翻转出的 {@code en_ud}）；其余语言由本模组手工维护在
     * {@code src/main/resources/assets/anvilcraft_pullwires/lang/} 下，与本体的做法一致。</p>
     *
     * @param provider 提供器
     */
    public static void init(RegistrumLangProvider provider) {
        ConfigData.readConfigClass(provider, AddonConfig.class);
        ConfigData.readConfigClass(provider, AddonClientConfig.class);

        provider.add("command.anvilcraft_pullwires.prefix", "[Pull Wires] ");
        provider.add("command.anvilcraft_pullwires.link.created", "Link created:");
        provider.add("command.anvilcraft_pullwires.link.line", "master %s - %s -> %s");
        provider.add("command.anvilcraft_pullwires.list.empty", "No links");
        provider.add("command.anvilcraft_pullwires.list.header", "%s link(s) in total");
        provider.add("command.anvilcraft_pullwires.list.line", "- %s | master %s - %s %s-> %s");
        provider.add("command.anvilcraft_pullwires.unlink.removed", "Removed %s link(s)");
        provider.add("command.anvilcraft_pullwires.unlink.none", "These two poles are not linked");
        provider.add("command.anvilcraft_pullwires.clear.done", "Cleared %s link(s)");
        provider.add(
            "command.anvilcraft_pullwires.error.no_pole",
            "No Transmission Pole top segment found near the given position; aim at any segment of the pole"
        );
        provider.add("command.anvilcraft_pullwires.error.same_pole", "Cannot link a pole to itself");
        provider.add(
            "command.anvilcraft_pullwires.error.duplicate_direction",
            "A link in this direction already exists (the reverse direction may coexist)"
        );
        provider.add("command.anvilcraft_pullwires.pole", "%s[%s]");
        provider.add("command.anvilcraft_pullwires.coords", "%s, %s, %s");

        provider.add("command.anvilcraft_pullwires.state.active", "Active");
        provider.add("command.anvilcraft_pullwires.state.inactive", "Not connected");
        provider.add("command.anvilcraft_pullwires.state.suspended", "Suspended");
        provider.add("command.anvilcraft_pullwires.state.pending", "Awaiting approval");

        provider.add("message.anvilcraft_pullwires.manual.started", "Master set to %s; place a Transmission Pole within range to link");
        provider.add("message.anvilcraft_pullwires.manual.too_far", "Out of manual connect range (%s / %s blocks)");
        provider.add("message.anvilcraft_pullwires.manual.connected", "Link created");
        provider.add("message.anvilcraft_pullwires.binding.bound", "Bound to master");
        provider.add("message.anvilcraft_pullwires.binding.cleared", "Binding cleared");
        provider.add("message.anvilcraft_pullwires.disk.bound", "Disk bound to master %s");
        provider.add("message.anvilcraft_pullwires.disk.paste_hint", " (right-click another pole to paste the link)");
        provider.add(
            "message.anvilcraft_pullwires.manual.same_pole",
            "This pole is the stored master; it cannot be linked to itself"
        );
        provider.add("message.anvilcraft_pullwires.manual.cancelled", "Too far from the master; binding cleared");
        provider.add(
            "message.anvilcraft_pullwires.manual.duplicate_direction",
            "A link in this direction already exists"
        );
        provider.add(
            "message.anvilcraft_pullwires.manual.cross_dimension_denied",
            "A link may only cross dimensions between two Cross Dimensional Poles"
        );
        provider.add("message.anvilcraft_pullwires.priority.changed", "Priority %s -> %s");
        provider.add("message.anvilcraft_pullwires.priority.limit", "Priority is already %s (range 0-15)");
        provider.add("message.anvilcraft_pullwires.lock.locked", "Locked: this pole ignores redstone switching");
        provider.add("message.anvilcraft_pullwires.lock.unlocked", "Unlocked: this pole follows redstone again");
        provider.add("message.anvilcraft_pullwires.sort.saved", "Reconnect sorting: applied to this pole");

        provider.add("screen.anvilcraft_pullwires.pole_sort.title", "Pole reconnect sorting");
        provider.add(
            "screen.anvilcraft_pullwires.pole_sort.roles",
            "Primary factor = %s, secondary factor = %s"
        );
        provider.add(
            "screen.anvilcraft_pullwires.pole_sort.time",
            "Time factor: ascending (fixed; earlier links are considered first)"
        );
        provider.add("screen.anvilcraft_pullwires.pole_sort.factor", "First: %s");
        provider.add("screen.anvilcraft_pullwires.pole_sort.factor.priority", "priority");
        provider.add("screen.anvilcraft_pullwires.pole_sort.factor.demand", "demand");
        provider.add("screen.anvilcraft_pullwires.pole_sort.primary.asc", "Primary: ascending");
        provider.add("screen.anvilcraft_pullwires.pole_sort.primary.desc", "Primary: descending");
        provider.add("screen.anvilcraft_pullwires.pole_sort.secondary.asc", "Secondary: ascending");
        provider.add("screen.anvilcraft_pullwires.pole_sort.secondary.desc", "Secondary: descending");
        provider.add("screen.anvilcraft_pullwires.pole_sort.label.priority", "Priority");
        provider.add(
            "screen.anvilcraft_pullwires.pole_sort.priority.value",
            "%s"
        );
        provider.add("screen.anvilcraft_pullwires.pole_sort.label.factor", "Primary factor");
        provider.add("screen.anvilcraft_pullwires.pole_sort.label.primary", "Primary direction");
        provider.add("screen.anvilcraft_pullwires.pole_sort.label.secondary", "Secondary direction");

        provider.add("tooltip.anvilcraft_pullwires.grid_information.exchange", "Total exchanged: %s");
        provider.add("tooltip.anvilcraft_pullwires.link.header", "Pull Wires:");
        provider.add("tooltip.anvilcraft_pullwires.link.upstream", "%s>>slave");
        provider.add("tooltip.anvilcraft_pullwires.link.downstream", "master>>%s");
        provider.add("tooltip.anvilcraft_pullwires.link.send", "Sending: %s");
        provider.add("tooltip.anvilcraft_pullwires.link.receive", "Receiving: %s");
        provider.add("tooltip.anvilcraft_pullwires.link.priority", "Priority: %s");
        provider.add("tooltip.anvilcraft_pullwires.locked", "Locked");
        provider.add("tooltip.anvilcraft_pullwires.item.hold_ctrl", "Hold [%s] for binding details");
        provider.add("tooltip.anvilcraft_pullwires.item.key_ctrl", "Ctrl");
        provider.add("tooltip.anvilcraft_pullwires.item.ranges", "Ranges:");
        provider.add("tooltip.anvilcraft_pullwires.item.supply", "Supply range: %s blocks");
        provider.add("tooltip.anvilcraft_pullwires.item.auto", "Auto-connect range: %s blocks");
        provider.add("tooltip.anvilcraft_pullwires.item.manual", "Manual connect range: %s blocks");
        provider.add("tooltip.anvilcraft_pullwires.item.unlimited", "No distance limit");
        provider.add("tooltip.anvilcraft_pullwires.item.bound", "Bound to master: %s (%s)");
        provider.add("tooltip.anvilcraft_pullwires.item.unbound", "Not bound (right-click a pole to bind, shift right-click to clear)");
        provider.add("bossbar.anvilcraft_pullwires.manual_link", "Connect distance %s / %s (break at %s)");
        provider.add("bossbar.anvilcraft_pullwires.cross_dimension", "Cross dimensional link: no distance limit");

        provider.add(
            "command.anvilcraft_pullwires.clear.confirm_hint",
            "Clearing is destructive; run /wires clear confirm to proceed"
        );
        provider.add("command.anvilcraft_pullwires.display.on", "Connection list: always on display");
        provider.add("command.anvilcraft_pullwires.display.off", "Connection list: hidden");
        provider.add(
            "command.anvilcraft_pullwires.guide.missing",
            "Ageratum is not installed, so the in-game guide cannot be opened"
        );
        provider.add("command.anvilcraft_pullwires.guide.opened", "Opened the Pull Wires guide");
        provider.add("hud.anvilcraft_pullwires.connection_list.header", "Links (%s)");
        provider.add("hud.anvilcraft_pullwires.connection_list.more", "%s more...");
        provider.add("hud.anvilcraft_pullwires.connection_list.transfer", "  - %s");
        provider.add(
            "hud.anvilcraft_pullwires.connection_list.transfer_lossy",
            "  - %s to %s (with loss)"
        );
        provider.add("key.categories.anvilcraft_pullwires", "Pull Wires");
        provider.add("key.anvilcraft_pullwires.pole_config", "Pole settings page");

        provider.add("dimension.minecraft.overworld", "Overworld");
        provider.add("dimension.minecraft.the_nether", "The Nether");
        provider.add("dimension.minecraft.the_end", "The End");

        // Jade 会为每个 provider 的配置项查这个键，缺失时会直接抛 AssertionError
        // （Missing config translation），表现是插件设置里显示原始键值、日志里刷红色异常。
        provider.add("config.jade.plugin_anvilcraft_pullwires.pole_info", "Pull Wires: Pole Info");

        provider.add(
            "jei.anvilcraft_pullwires.ultra_long_range_pole",
            """
            Ultra Long Range Pole: almost no supply range (2 blocks by default), but a huge auto-connect \
            range (256 blocks by default).
            Use it to merge two far apart grids into one without stringing poles all the way.
            Connection ranges are configured as multiples of AnvilCraft's Transmission Pole R, so changing \
            that R never breaks existing saves."""
        );
        provider.add(
            "jei.anvilcraft_pullwires.cross_dimensional_pole",
            """
            Cross Dimensional Pole: supplies nothing, never auto-connects and never draws a power line. \
            It is purely an endpoint for binding links.
            Two of them can link across dimensions, with no distance limit.
            While the link is live both tops emit portal particles; the pole also keeps its own single \
            chunk loaded so the transfer keeps running unattended."""
        );
    }
}
