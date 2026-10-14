package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.AdjustPriorityPacket;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 手持铁砧锤、按住功能键滚动滚轮来调整输电杆的结算优先级。
 *
 * <p>用 {@code InputEvent.MouseScrollingEvent}（本体 builder rod 的滚轮也走这个事件），
 * 只把滚轮方向发给服务端；实际数值由服务端在权威数据上夹取。</p>
 *
 * <p>条件不满足时<b>不</b>取消事件，把滚轮交回原版——否则玩家在快捷栏上滚轮切换物品会被吞掉。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID, value = Dist.CLIENT)
public class PriorityAdjustClientEvents {
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!AddonKeyMappings.PRIORITY_ADJUST.get().isDown()) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!(player.getMainHandItem().getItem() instanceof AnvilHammerItem)) return;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (PoleResolver.resolveTop(minecraft.level, hit.getBlockPos()) == null) return;

        double scroll = event.getScrollDeltaY();
        int delta = scroll > 0 ? 1 : scroll < 0 ? -1 : 0;
        if (delta == 0) return;

        PacketDistributor.sendToServer(new AdjustPriorityPacket(hit.getBlockPos(), delta));
        event.setCanceled(true);
    }
}
