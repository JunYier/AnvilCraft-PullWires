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
 * （见 {@link LinkBinding}），触发点是右键杆的那次交互（本体定日镜用的是物品 {@code useOn}，
 * 我们的杆继承自 {@code SimpleMultiPartBlock}、方块会把右键吃掉，所以只能走交互事件）。</p>
 *
 * <p>早期版本用一张服务端 {@code PENDING} 表记录待连接状态，那正是三类 bug 的同源成因：
 * 服务端状态与「玩家手上拿什么」是分离的，于是出现了「背包里所有同类杆都被附上光泽」
 * 「丢出去／创造模式删除后状态还挂着」「虚空连接」。数据搬到物品上之后，这三类问题从根上消失。</p>
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>{@link #tick}：手持物带绑定 → 显示距离 Boss 条；不带 → 隐藏。</li>
 *   <li>{@link #onPolePlaced}：放置杆时若手持物带绑定 → 按距离建立链路（可连续放置，绑定不消耗）。
 *       两端在不同维度时改按「跨维度规则」判定，见 {@link PoleKind#canCrossDimensions}。</li>
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
        // 主手优先，其次副手磁盘——「副手拿绑定磁盘、主手放杆」时同样要看得到距离条。
        ItemStack carrier = LinkBinding.carrierOf(player, allowOffhandDisk());
        if (carrier == null) {
            hideBar(player);
            return;
        }
        LinkBinding.Bound bound = LinkBinding.read(carrier);
        if (bound == null) {
            hideBar(player);
            return;
        }
        ServerLevel level = player.server.getLevel(bound.master().dimension());
        if (level == null) {
            LinkBinding.clear(carrier);
            hideBar(player);
            return;
        }
        // 只在这一格已经加载时校验主端还在不在。
        // 关键：未加载**不**等于「已经被拆了」——跨维度玩法下主端恰好就在不加载的区块里，
        // 若按未加载处理，玩家一过传送门手上刚绑好的主端就会被清掉。
        // 顺带也避免了「玩家在下界、每个 tick 强加载主世界一格方块」。
        if (level.isLoaded(bound.master().pos())
            && !PoleResolver.isPole(level, bound.master().pos())) {
            LinkBinding.clear(carrier);
            hideBar(player);
            return;
        }
        showBar(player, bound);
    }

    /** 「副手磁盘也能触发建链」的配置开关。 */
    private static boolean allowOffhandDisk() {
        return AnvilCraftPullWires.CONFIG.linking.allowDiskBinding;
    }

    // ------------------------------------------------------------------ 建链

    /**
     * 玩家放下了一根输电杆：按绑定把它连到主端。
     *
     * <p>绑定来自两只手里携带它的那一份：主手那根杆（手里有多少根就能连着放多少根），
     * 或<b>副手的磁盘</b>——「副手拿绑定磁盘、主手放杆」与本体磁盘 {@code onBlockPlaced}
     * 的用法一致，由 {@code linking.allowDiskBinding} 控制。</p>
     */
    public static void onPolePlaced(ServerPlayer player, ServerLevel level, BlockPos placedPos) {
        ItemStack carrier = LinkBinding.carrierOf(player, allowOffhandDisk());
        if (carrier == null) return;
        LinkBinding.Bound bound = LinkBinding.read(carrier);
        if (bound == null) return;

        BlockPos top = PoleResolver.resolveTop(level, placedPos);
        // 把杆放在主端自己身上没有意义，也不该报错。
        if (top == null || top.equals(bound.master().pos())) return;
        attemptLink(player, level, top, bound, carrier);
    }

    /**
     * 手持绑定物右键一根<b>已经放好</b>的杆：就地建链。
     *
     * <p>这是磁盘「粘贴」那一半。磁盘已经记着主端时，右键目标杆应当建链，而不是把目标杆
     * 覆盖成新的主端——否则玩家拿着刚绑好的磁盘走到另一根杆前，唯一能做的动作反而是
     * 把刚记下的主端丢掉。这也是跨维度连接的正常用法：主世界绑定 → 下界右键对端的杆。</p>
     *
     * @param carrier 真正握着绑定物的那一只手里的物品堆（可能是副手）
     */
    public static void onPolePasted(
        ServerPlayer player, ServerLevel level, BlockPos clickedPos, ItemStack carrier
    ) {
        LinkBinding.Bound bound = LinkBinding.read(carrier);
        if (bound == null) return;

        BlockPos top = PoleResolver.resolveTop(level, clickedPos);
        if (top == null) return;
        if (top.equals(bound.master().pos())) {
            player.displayClientMessage(message("manual.same_pole").withStyle(ChatFormatting.RED), true);
            return;
        }
        attemptLink(player, level, top, bound, carrier);
    }

    /**
     * 建链的公共核心：校验主端 → 准入判定 → 建立链路 → 反馈。
     *
     * <p>放置与粘贴共用同一份判定，避免两条路径的距离/维度规则漂移。</p>
     */
    private static void attemptLink(
        ServerPlayer player, ServerLevel level, BlockPos targetTop, LinkBinding.Bound bound, ItemStack carrier
    ) {
        PoleKey master = bound.master();
        ServerLevel masterLevel = player.server.getLevel(master.dimension());
        if (masterLevel == null) {
            LinkBinding.clear(carrier);
            return;
        }
        // 未加载**不等于**主端已被拆掉，所以只在已加载时校验。
        // 这一条正是跨维度连接能成立的前提——在主世界绑好、走到下界时，主世界那一格几乎一定是没加载的。
        // 顺带也避免了「玩家在下界、每个 tick 强加载主世界一格方块」。
        if (masterLevel.isLoaded(master.pos()) && !PoleResolver.isPole(masterLevel, master.pos())) {
            LinkBinding.clear(carrier);
            return;
        }

        // 主端的种类记在绑定里，因此这里不需要读主端那一格的状态（也就不会有跨维度的强加载）。
        PoleKind masterKind = bound.kind();
        boolean acrossDimensions = !level.dimension().equals(master.dimension());
        boolean masterCross = PoleKind.unlimitedConnectDistance(masterKind);
        boolean targetCross = PoleKind.of(level.getBlockState(targetTop)) == PoleKind.CROSS_DIMENSIONAL;

        // 跨维度时两端坐标不在同一个坐标系里，距离没有意义；判定函数内部会跳过距离那一档。
        double distance = acrossDimensions
            ? 0.0
            : Vec3.atCenterOf(targetTop).distanceTo(Vec3.atCenterOf(master.pos()));
        int range = PoleKind.manualConnectRange(masterKind);

        CrossDimensionRule.Verdict verdict = CrossDimensionRule.verdict(
            acrossDimensions, masterCross, targetCross,
            AnvilCraftPullWires.CONFIG.transmission.crossDimensionalPoleEnabled,
            distance, range
        );

        switch (verdict) {
            case CROSS_DIMENSION_DENIED -> {
                playFailureSound(player);
                player.displayClientMessage(
                    message("manual.cross_dimension_denied").withStyle(ChatFormatting.RED), true
                );
                return;
            }
            case TOO_FAR -> {
                // 与超距断开共用同一个音符盒音效。绑定保留，玩家换个位置继续即可。
                playFailureSound(player);
                player.displayClientMessage(message("manual.too_far", format(distance), String.valueOf(range))
                    .withStyle(ChatFormatting.RED), true);
                return;
            }
            case ALLOW -> {
            }
        }

        WireLinkRegistry registry = WireLinkRegistry.get(player.server);
        if (registry.createLink(master, PoleKey.of(level, targetTop)) == null) {
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
    private static void showBar(ServerPlayer player, LinkBinding.Bound bound) {
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

        // 跨维度杆没有距离这回事（需求 §5.2）：此时 Boss 条只用来确认「绑定还在」，
        // 因此满格、绿色、不报数字。若照旧按 range=0 算，玩家会看到一条永远爆红的进度条。
        if (PoleKind.unlimitedConnectDistance(bound.kind())) {
            bar.setProgress(1.0F);
            bar.setColor(BossEvent.BossBarColor.GREEN);
            bar.setName(Component.translatable("bossbar.anvilcraft_pullwires.cross_dimension"));
            return;
        }

        int range = PoleKind.manualConnectRange(bound.kind());
        double multiplier = AnvilCraftPullWires.CONFIG.transmission.cancelDistanceMultiplier;
        double distance = Vec3.atCenterOf(bound.master().pos()).distanceTo(player.getEyePosition());
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

    /** 退出世界时清空。 */
    public static void clearAll() {
        BARS.clear();
    }

    // ------------------------------------------------------------------ 内部

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