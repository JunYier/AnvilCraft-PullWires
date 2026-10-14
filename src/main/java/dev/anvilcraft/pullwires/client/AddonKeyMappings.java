package dev.anvilcraft.pullwires.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

/**
 * 本模组的键位。
 *
 * <p>只有优先级调整一个。<b>绑定 / 解绑不再有键位</b>——它们走物品自己的 {@code useOn}
 * （右键 / shift 右键），照抄本体定日镜的做法，因此不需要热键，也就不需要「按了热键要上报服务端」那条链路。</p>
 */
public class AddonKeyMappings {
    /** 优先级调整：手持铁砧锤时按住它滚动滚轮。 */
    public static final Lazy<KeyMapping> PRIORITY_ADJUST = Lazy.of(() -> new KeyMapping(
        "key.anvilcraft_pullwires.priority_adjust",
        KeyConflictContext.IN_GAME,
        Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        "key.categories.anvilcraft_pullwires"
    ));

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PRIORITY_ADJUST.get());
    }
}