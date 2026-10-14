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
        provider.add("message.anvilcraft_pullwires.manual.cancelled", "Pending link cancelled");
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

        provider.add("tooltip.anvilcraft_pullwires.grid_information.exchange", "Total exchanged: %s");
        provider.add("tooltip.anvilcraft_pullwires.link.master", "Pull Wires (master):");
        provider.add("tooltip.anvilcraft_pullwires.link.slave", "Pull Wires (slave):");
        provider.add("tooltip.anvilcraft_pullwires.link.both", "Pull Wires (master / slave):");
        provider.add("tooltip.anvilcraft_pullwires.link.send", "Sending: %s");
        provider.add("tooltip.anvilcraft_pullwires.link.receive", "Receiving: %s");
        provider.add("tooltip.anvilcraft_pullwires.link.priority", "Priority: %s");
        provider.add("tooltip.anvilcraft_pullwires.locked", "Locked");
        provider.add("bossbar.anvilcraft_pullwires.manual_link", "Connect distance %s / %s (break at %s)");
        provider.add("bossbar.anvilcraft_pullwires.cross_dimension", "Cross dimensional link: no distance limit");

        provider.add(
            "command.anvilcraft_pullwires.clear.confirm_hint",
            "Clearing is destructive; run /wires clear confirm to proceed"
        );
        provider.add("command.anvilcraft_pullwires.display.on", "Connection list: always on display");
        provider.add("command.anvilcraft_pullwires.display.off", "Connection list: hidden");
        provider.add("hud.anvilcraft_pullwires.connection_list.header", "Links (%s)");
        provider.add("hud.anvilcraft_pullwires.connection_list.more", "%s more...");
        provider.add("key.categories.anvilcraft_pullwires", "Pull Wires");
        provider.add("key.anvilcraft_pullwires.manual_link", "Manual link");
        provider.add("key.anvilcraft_pullwires.priority_adjust", "Adjust priority");

        provider.add("dimension.minecraft.overworld", "Overworld");
        provider.add("dimension.minecraft.the_nether", "The Nether");
        provider.add("dimension.minecraft.the_end", "The End");
    }
}
