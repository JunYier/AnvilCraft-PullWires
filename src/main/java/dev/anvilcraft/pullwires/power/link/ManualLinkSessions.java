package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.util.PoleResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 绑定与连接的服务端逻辑。
 *
 * <h2>状态在物品上，不在这里</h2>
 * <p>本类<b>不保存任何「谁在待连接」的状态</b>。绑定写在手持物的自定义数据里
 * （见 {@link LinkBinding}），触发点是物品自己的 {@code useOn}——这是照抄本体定日镜的做法。</p>
 *
 * <p>早期版本用一张服务端 {@code PENDING} 表记录待连接状态，那正是三类 bug 的同源成因：
 * 服务端状态与「玩家手上拿什么」是分离的，于是出现了「背包里所有同类杆都被附上光泽」
 * 「丢出去／创造模式删除后状态还挂着」「虚空连接」。数据搬到物品上之后，这三类问题从根上消失。</p>
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>{@link #tick}：手持物带绑定 → 显示距离 Boss 条；不带 → 隐藏。</li>
 *   <li>{@link #onPolePlaced}：放置杆时若手持物带绑定 → 按距离建立链路（可连续放置，绑定不消耗）。</li>
 * </ul>
 */
public final class ManualLinkSessions {
    /** 玩家 UUID → 距离 Boss 条。只发给该玩家自己。 */
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private ManualLinkSessions() {
    }

    // ------------------------------------------------------------------ 每 tick

    /** 绑定驱动 Boss 条：有绑定就显示，没有就隐藏。 */
    public static void tick(ServerPlayer player) {
        PoleKey master = LinkBinding.read(player.getMainHandItem());
        if (master == null || !masterStillExists(player, master)) {
            if (master != null) LinkBinding.clear(player.getMainHandItem());
            hideBar(player);
            return;
        }
        showBar(player, master);
    }

    // ------------------------------------------------------------------ 放置

    /** 玩家放下了一根输电杆。 */
    public static void onPolePlaced(ServerPlayer player, ServerLevel level, BlockPos placedPos) {
        ItemStack main = player.getMainHandItem();
        PoleKey master = LinkBinding.read(main);
        if (master == null) return;
        if (!level.dimension().equals(master.dimension())) return;
        if (!masterStillExists(player, master)) {
            LinkBinding.clear(main);
            return;
        }

        BlockPos top = PoleResolver.resolveTop(level, placedPos);
        if (top == null || top.equals(master.pos())) return;

        double distance = Vec3.atCenterOf(top).distanceTo(Vec3.atCenterOf(master.pos()));
        int range = PoleKind.manualConnectRangeAt(level, master.pos());
        if (distance > range) {
            // 与超距断开共用同一个音符盒音效。绑定保留，玩家换个位置继续放即可。
            playFailureSound(player);
            player.displayClientMessage(message("manual.too_far", format(distance), String.valueOf(range))
                .withStyle(ChatFormatting.RED), true);
            return;
        }

        WireLinkRegistry registry = WireLinkRegistry.get(player.server);
        if (registry.createLink(master, PoleKey.of(level, top)) == null) {
            player.displayClientMessage(message("manual.duplicate_direction").withStyle(ChatFormatting.RED), true);
            return;
        }
        AddonNetwork.broadcastRegistry(player.server);
        // 绑定**不消耗**：手里有多少根杆就能连着连多少根。
        player.displayClientMessage(message("manual.connected").withStyle(ChatFormatting.GREEN), false);
    }

    // ------------------------------------------------------------------ Boss 条

    /**
     * 连接距离的 Boss 条。
     *
     * <p>进度由 {@link ManualLinkRule#barProgress} 给出：前 3/4 绿、后 1/4 红，两段各自线性。
     * 用原版 {@link ServerBossEvent} 只发给本人，不需要任何客户端 Mixin。</p>
     */
    private static void showBar(ServerPlayer player, PoleKey master) {
        int range = PoleKind.manualConnectRangeAt(player.server.getLevel(master.dimension()), master.pos());
        double multiplier = AnvilCraftPullWires.CONFIG.transmission.cancelDistanceMultiplier;
        double distance = Vec3.atCenterOf(master.pos()).distanceTo(player.getEyePosition());
        boolean inRange = distance <= range;

        ServerBossEvent bar = BARS.get(player.getUUID());
        if (bar == null) {
            bar = new ServerBossEvent(
                Component.translatable("bossbar.anvilcraft_pullwires.manual_link"),
                BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.PROGRESS
            );
            bar.addPlayer(player);
            BARS.put(player.getUUID(), bar);
        }
        bar.setProgress(ManualLinkRule.barProgress(distance, range, multiplier));
        bar.setColor(inRange ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.RED);
        bar.setName(Component.translatable(
            "bossbar.anvilcraft_pullwires.manual_link",
            Component.literal(format(distance)).withStyle(inRange ? ChatFormatting.GREEN : ChatFormatting.RED),
            Component.literal(String.valueOf(range)).withStyle(ChatFormatting.GRAY),
            Component.literal(format(range * multiplier)).withStyle(ChatFormatting.DARK_RED)
        ));
    }

    private static void hideBar(ServerPlayer player) {
        ServerBossEvent bar = BARS.remove(player.getUUID());
        if (bar == null) return;
        bar.setVisible(false);
        bar.removeAllPlayers();
    }

    /** 退出世界时清空。 */
    public static void clearAll() {
        BARS.clear();
    }

    // ------------------------------------------------------------------ 内部

    /** 主端是否仍然存在。绑定可能指向一根已经被拆掉的杆——不校验就会连到空气上。 */
    private static boolean masterStillExists(ServerPlayer player, PoleKey master) {
        ServerLevel level = player.server.getLevel(master.dimension());
        return level != null && PoleResolver.isPole(level, master.pos());
    }

    /** 失败音效与超距断开共用。 */
    private static void playFailureSound(ServerPlayer player) {
        player.level().playSound(
            null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.5F
        );
    }

    private static MutableComponent message(String key, Object... args) {
        return Component.translatable("message.anvilcraft_pullwires." + key, args);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}