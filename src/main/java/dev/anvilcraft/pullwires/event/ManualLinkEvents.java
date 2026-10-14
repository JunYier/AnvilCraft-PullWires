package dev.anvilcraft.pullwires.event;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.power.link.ManualLinkSessions;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.WireLinkRegistry;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
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

    /**
     * 拆掉输电杆时回收它的链路。
     *
     * <p>不做这一步的话，链路定义会一直留在注册表里——电力线继续渲染、结算继续引用一个已经不存在的杆。</p>
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getState().is(ModBlocks.TRANSMISSION_POLE.get())
            && !event.getState().is(ModBlocks.REMOTE_TRANSMISSION_POLE.get())) {
            return;
        }
        BlockPos top = PoleResolver.resolveTop(level, event.getPos());
        if (top == null) return;

        WireLinkRegistry registry = WireLinkRegistry.get(level.getServer());
        PoleKey key = PoleKey.of(level, top);
        boolean hadLinks = !registry.linksOf(key).isEmpty();
        registry.removePole(key);
        if (hadLinks) AddonNetwork.broadcastLinks(level.getServer());
    }
}
