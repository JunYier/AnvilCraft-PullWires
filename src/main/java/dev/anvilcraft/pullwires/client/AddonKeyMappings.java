package dev.anvilcraft.pullwires.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

public class AddonKeyMappings {
    /** 手动连接热键，默认左 Ctrl；与本体 building rod 的同键位不冲突（触发条件不同）。 */
    public static final Lazy<KeyMapping> MANUAL_LINK = Lazy.of(() -> new KeyMapping(
        "key.anvilcraft_pullwires.manual_link",
        KeyConflictContext.IN_GAME,
        Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        "key.categories.anvilcraft_pullwires"
    ));

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MANUAL_LINK.get());
    }
}
