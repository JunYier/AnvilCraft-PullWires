package dev.anvilcraft.pullwires.data.lang;

import dev.anvilcraft.lib.v2.config.ConfigData;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumLangProvider;
import dev.anvilcraft.pullwires.AddonConfig;

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
        provider.add("command.anvilcraft_pullwires.pole", "%s[%s]");
        provider.add("command.anvilcraft_pullwires.coords", "%s, %s, %s");

        provider.add("command.anvilcraft_pullwires.state.active", "Active");
        provider.add("command.anvilcraft_pullwires.state.inactive", "Not connected");
        provider.add("command.anvilcraft_pullwires.state.suspended", "Suspended");
        provider.add("command.anvilcraft_pullwires.state.pending", "Awaiting approval");

        provider.add("message.anvilcraft_pullwires.manual.started", "Master set to %s; place a Transmission Pole within range to link");
        provider.add("message.anvilcraft_pullwires.manual.too_far", "Out of manual connect range (%s / %s blocks)");
        provider.add("message.anvilcraft_pullwires.manual.connected", "Link created");
        provider.add("message.anvilcraft_pullwires.manual.cancelled", "Pending link cancelled");
        provider.add("hud.anvilcraft_pullwires.manual_link.distance", "Connect distance %s / %s");

        provider.add("key.categories.anvilcraft_pullwires", "Pull Wires");
        provider.add("key.anvilcraft_pullwires.manual_link", "Manual link");

        provider.add("dimension.minecraft.overworld", "Overworld");
        provider.add("dimension.minecraft.the_nether", "The Nether");
        provider.add("dimension.minecraft.the_end", "The End");
    }
}
