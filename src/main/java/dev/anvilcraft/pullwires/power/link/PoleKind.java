package dev.anvilcraft.pullwires.power.link;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.init.AddonBlocks;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.TransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.state.Vertical3PartHalf;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 杆的种类。三档范围<b>按种类</b>取值，与本体「按杆子种类划分 range」的做法一致。
 *
 * <p>新增的两种杆目前复用远程输电杆的模型与贴图，但它们是独立方块、独立方块实体，
 * 因此也能拥有自己的范围预设。</p>
 */
public enum PoleKind {
    /** 本体的输电杆。供电范围取自本体配置，本模组只管自动 / 手动连接范围。 */
    TRANSMISSION,
    /** 本体的远程输电桩。 */
    REMOTE,
    /** 超远程输电杆：供电范围极小、连接范围极大（纯数值预设）。 */
    ULTRA_LONG_RANGE,
    /** 跨维度输电杆：不供电、不自动组网，只作为磁盘连接的端点。 */
    CROSS_DIMENSIONAL;

    /**
     * 从方块状态判断种类；不是本模组关心的杆时返回 {@code null}。
     *

     */
    public static @Nullable PoleKind of(BlockState state) {
        if (state.is(ModBlocks.TRANSMISSION_POLE.get())) return TRANSMISSION;
        if (state.is(ModBlocks.REMOTE_TRANSMISSION_POLE.get())) return REMOTE;
        if (state.is(AddonBlocks.ULTRA_LONG_RANGE_POLE.get())) return ULTRA_LONG_RANGE;
        if (state.is(AddonBlocks.CROSS_DIMENSIONAL_POLE.get())) return CROSS_DIMENSIONAL;
        return null;
    }


    // ---------------------------------------------------------------- 取值入口

    /**
     * 该种类解析后的三档范围。
     *
     * <p>配置里存的是「本体 R 的倍数」，这里换算成方块数（见 {@link PoleRangeRule}）。
     * 换算一律走 {@link dev.anvilcraft.pullwires.AddonConfig.Transmission#rangesFor}，
     * 它需要本体的 R——那是<b>服务器配置</b>，客户端读到的是登录时同步下来的值。</p>
     */
    public static PoleRangeRule.Ranges ranges(@Nullable PoleKind kind) {
        return AnvilCraftPullWires.CONFIG.transmission.rangesFor(kind == null ? TRANSMISSION : kind);
    }

    /** 该种类的供电范围；本体那两种杆取本体配置，我们自己的两种取本模组配置。 */
    public static int supplyRange(@Nullable PoleKind kind) {
        return ranges(kind).supplyRange();
    }

    /** 该种类的自动连接范围；种类未知时按本体输电杆处理。 */
    public static int autoConnectRange(@Nullable PoleKind kind) {
        return ranges(kind).autoConnectRange();
    }

    /** 该种类的手动连接范围。 */
    public static int manualConnectRange(@Nullable PoleKind kind) {
        return ranges(kind).manualConnectRange();
    }

    /** 某坐标上那根杆的自动连接范围。 */
    public static int autoConnectRangeAt(@Nullable Level level, BlockPos pos) {
        return autoConnectRange(level == null ? null : of(level.getBlockState(pos)));
    }

    /** 某坐标上那根杆的手动连接范围。 */
    public static int manualConnectRangeAt(@Nullable Level level, BlockPos pos) {
        return manualConnectRange(level == null ? null : of(level.getBlockState(pos)));
    }

    /**
     * 从物品推出杆的种类；不是本模组认得的杆时返回 {@code null}。
     *
     * <p>这是「手上拿的是哪种杆」的唯一入口。之前的范围取值方法各自抄了一遍
     * {@code Block.byItem} + {@code defaultBlockState}，再新增一处就又多一份实现。</p>
     */
    public static @Nullable PoleKind kindOfItem(ItemStack stack) {
        Block block = Block.byItem(stack.getItem());
        return block == Blocks.AIR ? null : of(block.defaultBlockState());
    }

    /**
     * 该种类的杆是否<b>不受连接距离限制</b>。
     *
     * <p>目前只有跨维度杆：需求 §5.2 规定它同维度、跨维度都没有距离限制。
     * 判据取<b>主端</b>的种类——距离量的本来就是「主端到目标点」。
     * 维度那一侧的准入条件见 {@link CrossDimensionRule#verdict}。</p>
     */
    public static boolean unlimitedConnectDistance(@Nullable PoleKind kind) {
        return kind == CROSS_DIMENSIONAL;
    }

    /** 手持某物品时应当使用的自动连接范围（放置预览用）。 */
    public static int autoConnectRangeOfItem(ItemStack stack) {
        return autoConnectRange(kindOfItem(stack));
    }


    /** 从元件推种类（只有方块实体才带方块状态）。 */
    public static @Nullable PoleKind ofComponent(Object component) {
        return component instanceof BlockEntity entity ? of(entity.getBlockState()) : null;
    }

    /** 手持某物品时应当使用的手动连接范围（引导带与放置判定共用）。 */
    public static int manualConnectRangeOfItem(ItemStack stack) {
        return manualConnectRange(kindOfItem(stack));
    }

    /** 该物品是不是本模组认得的电线杆（四种都算）。 */
    public static boolean isPoleItem(ItemStack stack) {
        return kindOfItem(stack) != null;
    }

    /**
     * 该方块状态是否是指定半段（{@code top} 为真取顶段，否则取底段）。
     *
     * <p>三段与四段的多部分方块用的是不同的 HALF 属性类型，这里统一判掉，
     * 避免调用方再各自写一遍 {@code state.is(...)} 硬编码清单。</p>
     */
    public static boolean isHalf(BlockState state, boolean top) {
        if (state.is(ModBlocks.TRANSMISSION_POLE.get())) {
            return (state.getValue(TransmissionPoleBlock.HALF) == Vertical3PartHalf.TOP) == top;
        }
        if (of(state) != null) {
            return (state.getValue(RemoteTransmissionPoleBlock.HALF) == Vertical4PartHalf.TOP) == top;
        }
        return false;
    }

    /** 该种类是否参与自动组网（跨维度杆不参与）。 */
    public boolean joinsGrid() {
        return this != CROSS_DIMENSIONAL;
    }

    /** 该种类的供电范围是否也由本模组配置（本体那两种杆取自本体配置）。 */
    public boolean isAddonOwned() {
        return this == ULTRA_LONG_RANGE || this == CROSS_DIMENSIONAL;
    }
}