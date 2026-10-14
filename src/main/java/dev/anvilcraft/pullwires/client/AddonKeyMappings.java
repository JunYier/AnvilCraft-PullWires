package dev.anvilcraft.pullwires.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

public class AddonKeyMappings {
    /** 手动连接：手持输电杆时按住它右键已放置的杆。 */
    public static final Lazy<KeyMapping> MANUAL_LINK = Lazy.of(() -> register(
        "manual_link", GLFW.GLFW_KEY_LEFT_CONTROL
    ));

    /** 优先级调整：手持铁砧锤时按住它滚动滚轮。 */
    public static final Lazy<KeyMapping> PRIORITY_ADJUST = Lazy.of(() -> register(
        "priority_adjust", GLFW.GLFW_KEY_LEFT_CONTROL
    ));

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MANUAL_LINK.get());
        event.register(PRIORITY_ADJUST.get());
    }

    private static KeyMapping register(String name, int defaultKey) {
        return new KeyMapping(
            "key.anvilcraft_pullwires." + name,
            KeyConflictContext.IN_GAME,
            Type.KEYSYM,
            defaultKey,
            "key.categories.anvilcraft_pullwires"
        );
    }
}
