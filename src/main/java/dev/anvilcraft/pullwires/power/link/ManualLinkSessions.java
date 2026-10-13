package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.network.AddonNetwork;
import dev.anvilcraft.pullwires.network.SyncPendingLinkPacket;
import dev.anvilcraft.pullwires.util.PoleResolver;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 手动连接的待连接状态（服务端权威）。
 *
 * <h2>主端与从端</h2>
 * <p><b>被连接方（已经放置、被右键的那根杆）是主端，连接方（玩家手上那根）是从端。</b>
 * 主端持有管理权：结算优先级、亏电断路、重连裁决都挂在它身上。</p>
 *
 * <h2>清除条件（穷举，其余情况一律保持）</h2>
 * <ol>
 *   <li>手持输电杆、按住热键<b>右键空气</b>（显式取消）；</li>
 *   <li>玩家<b>死亡</b>；</li>
 *   <li><b>退出世界</b>；</li>
 *   <li><b>成功建立链路</b>；</li>
 *   <li>瞄准点到主端顶端的直线距离超过 <b>手动连接范围 × 放弃倍数</b>（默认 2 倍），
 *       触发时清除状态、播放音符盒音效并清空附魔光泽。</li>
 * </ol>
 * <p><b>明确不作为清除条件</b>：超时、切换手持物、潜行、被击退、主端区块卸载。</p>
 *
 * <h2>附魔光泽</h2>
 * <p>用原版的 {@code ENCHANTMENT_GLINT_OVERRIDE} 组件来做——它本身就是「强制发光」的开关，
 * 不需要为本模组注册自定义组件，而且组件会被正常同步给客户端。</p>
 */
public final class ManualLinkSessions {
    /** 玩家 UUID → 主端坐标。 */
    private static final Map<UUID, PoleKey> PENDING = new HashMap<>();

    private ManualLinkSessions() {
    }

    // ------------------------------------------------------------------ 建立

    /** 玩家按住热键右键了一根杆：进入待连接状态。返回是否成功。 */
    public static boolean start(ServerPlayer player, BlockPos clickedPos) {
        if (!isPoleItem(player.getMainHandItem())) return false;
        BlockPos top = PoleResolver.resolveTop(player.level(), clickedPos);
        if (top == null) return false;

        PoleKey master = PoleKey.of(player.level(), top);
        PENDING.put(player.getUUID(), master);
        clearGlint(player);
        setGlint(player.getMainHandItem(), true);
        syncPending(player, top);
        player.displayClientMessage(
            message("manual.started", poleText(master)).withStyle(ChatFormatting.GOLD),
            true
        );
        return true;
    }

    /** 清除待连接状态；{@code announce} 为假时保持安静（死亡、掉线用）。 */
    public static void cancel(ServerPlayer player, boolean announce) {
        if (PENDING.remove(player.getUUID()) == null) return;
        clearGlint(player);
        syncPending(player, null);
        if (announce) {
            player.displayClientMessage(message("manual.cancelled").withStyle(ChatFormatting.GRAY), true);
        }
    }

    public static boolean isPending(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------------ 每 tick

    /** 距离超出放弃距离时清除待连接状态并播放音符盒音效。 */
    public static void tick(ServerPlayer player) {
        PoleKey master = PENDING.get(player.getUUID());
        if (master == null) return;
        if (!player.level().dimension().equals(master.dimension())) {
            cancel(player, true);
            return;
        }
        double distance = aimDistance(player, master);
        if (feedback(distance) != ManualLinkFeedback.CANCEL) return;
        cancel(player, false);
        playCancelSound(player);
    }

    // ------------------------------------------------------------------ 放置

    /** 玩家放下了一根输电杆。 */
    public static void onPolePlaced(ServerPlayer player, ServerLevel level, BlockPos placedPos) {
        PoleKey master = PENDING.get(player.getUUID());
        if (master == null) return;
        BlockPos top = PoleResolver.resolveTop(level, placedPos);
        if (top == null || top.equals(master.pos())) return;
        if (!level.dimension().equals(master.dimension())) {
            cancel(player, true);
            return;
        }

        double distance = Vec3.atCenterOf(top).distanceTo(Vec3.atCenterOf(master.pos()));
        switch (feedback(distance)) {
            case CANCEL -> {
                cancel(player, false);
                playCancelSound(player);
            }
            case OUT_OF_RANGE -> player.displayClientMessage(
                message(
                    "manual.too_far",
                    format(distance),
                    String.valueOf(AnvilCraftPullWires.CONFIG.powerLink.manualConnectRange)
                ).withStyle(ChatFormatting.RED),
                true
            );
            case IN_RANGE -> {
                WireLinkRegistry.get(player.server).createLink(master, PoleKey.of(level, top));
                PENDING.remove(player.getUUID());
                clearGlint(player);
                syncPending(player, null);
                AddonNetwork.broadcastLinks(player.server);
                player.displayClientMessage(
                    message("manual.connected", poleText(master)).withStyle(ChatFormatting.GREEN),
                    false
                );
            }
        }
    }

    /** 退出世界时清空。 */
    public static void clearAll() {
        PENDING.clear();
    }

    // ------------------------------------------------------------------ 内部

    private static ManualLinkFeedback feedback(double distance) {
        return ManualLinkRule.feedback(
            distance,
            AnvilCraftPullWires.CONFIG.powerLink.manualConnectRange,
            AnvilCraftPullWires.CONFIG.powerLink.cancelDistanceMultiplier
        );
    }

    /**
     * 玩家眼睛到主端顶端的直线距离。
     *
     * <p><b>必须从玩家量，不能从准心命中点量。</b>曾经用 {@code player.pick()} 的命中位置当起点，
     * 结果是「看着杆走近走远都不断、一转头就断」——命中点随视角跳到远处地形上，距离瞬间爆掉。
     * 而方块放置距离只有 5 格左右，放置点必然紧贴玩家，所以「玩家到主端」与「放置点到主端」实质等价。</p>
     */
    private static double aimDistance(ServerPlayer player, PoleKey master) {
        return player.getEyePosition().distanceTo(Vec3.atCenterOf(master.pos()));
    }

    private static void playCancelSound(ServerPlayer player) {
        player.level().playSound(
            null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.5F
        );
    }

    /** 把待连接状态同步给客户端，客户端靠它决定要不要画粒子束。 */
    private static void syncPending(ServerPlayer player, @Nullable BlockPos master) {
        PacketDistributor.sendToPlayer(player, new SyncPendingLinkPacket(master));
    }

    private static boolean isPoleItem(ItemStack stack) {
        return stack.is(ModBlocks.TRANSMISSION_POLE.get().asItem());
    }

    private static void setGlint(ItemStack stack, boolean glint) {
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
    }

    /**
     * 清掉背包里所有输电杆上的强制发光。
     *
     * <p>只清主手是不够的：玩家可能在待连接期间把杆挪到别的格子，组件会跟着物品走。</p>
     */
    private static void clearGlint(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            stacks.add(player.getInventory().getItem(slot));
        }
        stacks.add(player.containerMenu.getCarried());
        for (ItemStack stack : stacks) {
            if (isPoleItem(stack) && stack.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) {
                setGlint(stack, false);
            }
        }
    }

    private static @Nullable Component poleText(PoleKey key) {
        return Component.translatable(
            "command.anvilcraft_pullwires.pole",
            Component
                .translatableWithFallback(
                    "dimension." + key.dimension().location().toLanguageKey(),
                    key.dimension().location().toString()
                ),
            Component.literal(
                key.pos().getX() + ", " + key.pos().getY() + ", " + key.pos().getZ()
            )
        );
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static MutableComponent message(String key, Object... args) {
        return Component.translatable("message.anvilcraft_pullwires." + key, args);
    }
}
