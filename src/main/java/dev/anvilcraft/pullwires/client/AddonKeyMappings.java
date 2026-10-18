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
 * <p>只有「输电杆配置页」一个。它同时承担两件事：手持铁砧锤时按住它<b>右键</b>一根杆打开那根杆的
 * 配置页；在物品提示里它就是「按住看绑定细节」的那个键。
 * 原来的「按住它滚动滚轮改优先级」<b>已并入配置页</b>（V1.12.12），因此世界上不再有第二条通路。</p>
 *
 * <p><b>绑定 / 解绑不再有键位</b>——它们走物品自己的 {@code useOn}（右键 / shift 右键），
 * 照抄本体定日镜的做法，因此不需要热键，也就不需要「按了热键要上报服务端」那条链路。</p>
 */
public class AddonKeyMappings {
    /** 输电杆配置页：手持铁砧锤时按住它右键一根杆。 */
    public static final Lazy<KeyMapping> POLE_CONFIG = Lazy.of(() -> new KeyMapping(
        "key.anvilcraft_pullwires.pole_config",
        KeyConflictContext.IN_GAME,
        Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        "key.categories.anvilcraft_pullwires"
    ));

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(POLE_CONFIG.get());
    }
}