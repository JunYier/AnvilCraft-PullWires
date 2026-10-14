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
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
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

    /** 玩家 UUID → 距离 Boss 条。只发给该玩家自己。 */
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private ManualLinkSessions() {
    }

    // ------------------------------------------------------------------ 建立

    /** 玩家按住热键右键了一根杆：进入待连接状态。返回是否成功。 */
    public static boolean start(ServerPlayer player, BlockPos clickedPos) {
        if (!isPoleItem(player.getMainHandItem())) return false;
        BlockPos top = PoleResolver.resolveTop(player.level(), clickedPos);
        if (top == null) return false;

        PoleKey master = PoleKey.of(player.level(), top);
        // 按住热键才有权限进入链接状态；关掉这个开关后，只剩「副手绑定磁盘」那条路。
        if (!AnvilCraftPullWires.CONFIG.linking.enableManualLinkByHotkey) return false;
        // 副手拿着磁盘时，热键右键同时把它绑定到这个主端（不碰本体的方块数据）。
        if (AnvilCraftPullWires.CONFIG.linking.allowDiskBinding) {
            DiskBinding.bind(player.getOffhandItem(), master);
        }
        PENDING.put(player.getUUID(), master);
        clearGlint(player);
        setGlint(player.getMainHandItem(), true);
        syncPending(player, top);
        showBar(player);
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
        hideBar(player);
        if (announce) {
            player.displayClientMessage(message("manual.cancelled").withStyle(ChatFormatting.GRAY), true);
        }
    }

    public static boolean isPending(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }


    /**
     * 副手绑定磁盘路径：主手未进入链接状态，只在放下输电杆时尝试建立一次连接。
     *
     * <p>与本体 {@code DiskItem.onBlockPlaced}（副手磁盘 + 放置自动粘贴）同一思路，
     * 但这里只做「建立连接」一件事，不碰目标杆的任何其它配置。</p>
     */
    public static void tryOffhandDiskLink(ServerPlayer player, ServerLevel level, BlockPos placedPos) {
        if (!AnvilCraftPullWires.CONFIG.linking.allowDiskBinding) return;
        // 已经在链接状态时由 onPolePlaced 负责，避免同一根杆被连两次。
        if (PENDING.containsKey(player.getUUID())) return;

        PoleKey master = DiskBinding.read(player.getOffhandItem());
        if (master == null || !level.dimension().equals(master.dimension())) return;

        BlockPos top = PoleResolver.resolveTop(level, placedPos);
        if (top == null || top.equals(master.pos())) return;

        double distance = Vec3.atCenterOf(top).distanceTo(Vec3.atCenterOf(master.pos()));
        if (feedback(distance) != ManualLinkFeedback.IN_RANGE) {
            playCancelSound(player);
            return;
        }
        PoleKey slave = PoleKey.of(level, top);
        if (WireLinkRegistry.get(player.server).createLink(master, slave) == null) return;
        AddonNetwork.broadcastRegistry(player.server);
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
        // 自愈：生存模式放下杆会把手上的那一堆消耗掉，附魔光泽跟着一起没了；
        // 拆回来的掉落物是新建的物品堆，同样不带光泽。所以每 tick 补一次。
        refreshGlint(player);

        double distance = aimDistance(player, master);
        updateBar(player, distance);
        if (feedback(distance) != ManualLinkFeedback.CANCEL) return;
        cancel(player, false);
        playCancelSound(player);
    }

    private static void refreshGlint(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (isPoleItem(held) && !held.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) {
            setGlint(held, true);
        }
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
            case OUT_OF_RANGE -> {
                // 与超距断开共用同一个音符盒音效，让「失败了」这件事有一致的听觉反馈。
                playCancelSound(player);
                player.displayClientMessage(
                    message(
                        "manual.too_far",
                        format(distance),
                        String.valueOf(AnvilCraftPullWires.CONFIG.transmission.manualConnectRange)
                    ).withStyle(ChatFormatting.RED),
                    true
                );
            }
            case IN_RANGE -> {
                if (WireLinkRegistry.get(player.server).createLink(master, PoleKey.of(level, top)) == null) {
                    // 同向重复：拒绝但不打断链接状态，玩家可以换个位置继续放。
                    player.displayClientMessage(
                        message("manual.duplicate_direction").withStyle(ChatFormatting.RED), true
                    );
                    return;
                }
                // V1.11.0：放下即建立，但**保持链接状态**。
                // 手上拿多少根杆就能连着连多少根，不必每根都重新绑定一次。
                AddonNetwork.broadcastRegistry(player.server);
                player.displayClientMessage(
                    message("manual.connected").withStyle(ChatFormatting.GREEN),
                    false
                );
            }
        }
    }

    /** 退出世界时清空。 */
    public static void clearAll() {
        PENDING.clear();
        // 玩家已经离线，不需要再发包。
        BARS.clear();
    }

    // ------------------------------------------------------------------ Boss 条

    /**
     * 连接距离的 Boss 条。
     *
     * <p>前 {@link ManualLinkRule#GREEN_SEGMENT_END} 是绿色（还在手动连接范围内），
     * 后 1/4 转红（已超出、正在逼近断开），两段各自线性、斜率不同。到达断开距离时
     * 由 {@link #tick} 清掉待连接状态，Boss 条随之消失。</p>
     *
     * <p>用原版 {@link ServerBossEvent} 只发给本人，不需要任何客户端 Mixin。</p>
     */
    private static void showBar(ServerPlayer player) {
        hideBar(player);
        ServerBossEvent bar = new ServerBossEvent(
            Component.translatable("bossbar.anvilcraft_pullwires.manual_link"),
            BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.PROGRESS
        );
        bar.setProgress(0.0F);
        bar.addPlayer(player);
        BARS.put(player.getUUID(), bar);
    }

    private static void updateBar(ServerPlayer player, double distance) {
        ServerBossEvent bar = BARS.get(player.getUUID());
        if (bar == null) return;

        int range = AnvilCraftPullWires.CONFIG.transmission.manualConnectRange;
        double multiplier = AnvilCraftPullWires.CONFIG.transmission.cancelDistanceMultiplier;
        boolean inRange = distance <= range;

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

    // ------------------------------------------------------------------ 内部

    private static ManualLinkFeedback feedback(double distance) {
        return ManualLinkRule.feedback(
            distance,
            AnvilCraftPullWires.CONFIG.transmission.manualConnectRange,
            AnvilCraftPullWires.CONFIG.transmission.cancelDistanceMultiplier
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
