package dev.anvilcraft.pullwires.event;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.power.link.ManualLinkSessions;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 手动连接的服务端事件入口。
 *
 * <p>覆盖需求里列明的四条被动清除条件：死亡、退出世界、成功建立（见 {@link #onPlace}），
 * 以及超出放弃距离（见 {@link #onPlayerTick}）。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID)
public class ManualLinkEvents {
    /** 逐玩家检查是否已离主端太远；只有处于待连接状态时才会真的做射线检测。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ManualLinkSessions.tick(player);
        }
    }

    /** 放下输电杆：若正处于待连接状态，按直线距离决定建立、拒绝还是放弃。 */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getPlacedBlock().is(ModBlocks.TRANSMISSION_POLE.get())) return;
        ManualLinkSessions.onPolePlaced(player, level, event.getPos());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ManualLinkSessions.cancel(player, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ManualLinkSessions.cancel(player, false);
        }
    }

    /** 进服务器时推一份链路列表，否则客户端要等到下一次链路变化才知道该画哪些线。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AddonNetwork.sendLinks(player);
        }
    }
}
