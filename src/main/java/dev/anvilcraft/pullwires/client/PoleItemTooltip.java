package dev.anvilcraft.pullwires.client;

import com.mojang.datafixers.util.Either;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.power.link.LinkBinding;
import dev.anvilcraft.pullwires.power.link.PoleConfig;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.dubhe.anvilcraft.api.tooltip.providers.ITooltipProvider;
import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * 电线杆<b>物品</b>的 tooltip（需求 §5.3）。
 *
 * <p>与放在地上的杆不同，物品在背包里没有「事实上的角色」——它还没被放下去。
 * 因此这里显示的是<b>这根杆是什么</b>（三档范围，需要按住 Shift）与
 * <b>它现在能不能直接连</b>（绑定状态，常驻）。</p>
 *
 * <h2>完全照抄本体的协议（V1.12.7 修正）</h2>
 * <p>本体在 {@code AnvilCraft} 里统一调 {@code ItemTooltipManager.addTooltip(...)}，规则是：</p>
 * <ol>
 *   <li>始终显示普通信息；</li>
 *   <li>按住 Shift 时追加详情；</li>
 *   <li>没按 Shift 时补一行提示
 *       {@code tooltip.anvilcraft.press_key} + {@code tooltip.anvilcraft.key} + {@code key.keyboard.left.shift}
 *       —— 也就是「按住 [左Shift] 以查看更多信息」。</li>
 * </ol>
 * <p>这里直接复用本体那两个翻译键，因此本模组的杆与本体那些物品提示<b>长得一模一样</b>，
 * 玩家不需要学第二套约定。</p>
 *
 * <p><b>必须用 {@link Screen#hasShiftDown()} 而不是 {@code player.isShiftKeyDown()}</b>：
 * 后者读的是实体的潜行状态，在背包界面里输入不再被处理，那个标志经常是过期的——
 * 这正是之前「按住 Shift 没反应」的真因。本体也是用 {@code Screen.hasShiftDown()}。</p>
 *
 * <p>「三档范围数值」是需求点名的：玩家要能在摆放之前就知道这根杆能连多远，
 * 否则只能靠反复试放。数值取自与服务端同一份换算（{@link PoleKind}），因此不会出现
 * 「tooltip 写 16 格、实际只认 12 格」。</p>
 */
@EventBusSubscriber(modid = AnvilCraftPullWires.MOD_ID, value = Dist.CLIENT)
public final class PoleItemTooltip {
    private PoleItemTooltip() {
    }

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        PoleKind kind = PoleKind.kindOfItem(stack);
        if (kind == null && !(stack.getItem() instanceof DiskItem)) return;

        // **四种杆一视同仁**：只要是本模组认得的杆物品，按住 Shift 都能看到三档范围。
        // 不按杆型分流——「这根杆能连多远」对四种杆都是同样的信息。
        if (kind != null) {
            if (Screen.hasShiftDown()) {
                appendRanges(event, kind);
            } else {
                appendPressKeyHint(event);
            }
        }
        // 杆与磁盘都能携带绑定，所以这条两种物品都要显示。
        appendBinding(event, stack);
    }

    /**
     * 「按住 [左Shift] 以查看更多信息」——与本体完全相同的两个键。
     *
     * <p>直接复用本体的 {@code tooltip.anvilcraft.press_key} / {@code tooltip.anvilcraft.key}，
     * 因此中英文文本都与本体其它物品一致，不需要我们维护第二份。</p>
     */
    private static void appendPressKeyHint(RenderTooltipEvent.GatherComponents event) {
        event.getTooltipElements().add(Either.left(
            Component.translatable(
                "tooltip.anvilcraft.press_key",
                Component.translatable(
                    "tooltip.anvilcraft.key",
                    Component.translatable("key.keyboard.left.shift").withStyle(ChatFormatting.WHITE)
                )
            ).withStyle(ChatFormatting.DARK_GRAY)
        ));
    }

    private static void appendRanges(RenderTooltipEvent.GatherComponents event, PoleKind kind) {
        int supply = PoleKind.supplyRange(kind);
        int auto = PoleKind.autoConnectRange(kind);
        int manual = PoleKind.manualConnectRange(kind);

        event.getTooltipElements().add(Either.left(
            Component.translatable("tooltip.anvilcraft_pullwires.item.ranges").withStyle(ChatFormatting.BLUE)
        ));
        // 跨维度杆不供电、不组网，那两行的数值都是 0——0 不显示，免得版面全是 0 格。
        if (supply > 0) {
            event.getTooltipElements().add(Either.left(line("item.supply", supply)));
        }
        if (auto > 0) {
            event.getTooltipElements().add(Either.left(line("item.auto", auto)));
        }
        if (manual > 0) {
            event.getTooltipElements().add(Either.left(line("item.manual", manual)));
        } else {
            // 手动范围为 0 只有跨维度杆（它不受距离限制）。
            event.getTooltipElements().add(Either.left(
                Component.translatable("tooltip.anvilcraft_pullwires.item.unlimited").withStyle(ChatFormatting.GRAY)
            ));
        }
    }

    /**
     * 绑定相关的行。
     *
     * <p><b>「已绑定主端…」常驻，「未绑定…」与「优先级…」移到按住左 Ctrl 之后</b>（V1.12.11）：
     * 前者回答的是「这根杆现在连着谁」，不按键也该看得见；后两者是细节，常驻会把版面撑满。
     * 磁盘同理——它携带的是同一种绑定数据。</p>
     *
     * <p>用 {@link Screen#hasControlDown()}（原始按键状态）而不是实体状态：
     * 在背包界面里后者经常是过期的，这正是之前「按住 Shift 没反应」的根因。
     * 提示文案必须自建——本体的 {@code tooltip.anvilcraft.press_key} 把键写死成了左 Shift。</p>
     */
    private static void appendBinding(RenderTooltipEvent.GatherComponents event, ItemStack stack) {
        LinkBinding.Bound bound = LinkBinding.read(stack);
        boolean details = Screen.hasControlDown();
        if (bound == null) {
            if (details) {
                event.getTooltipElements().add(Either.left(
                    Component.translatable("tooltip.anvilcraft_pullwires.item.unbound")
                        .withStyle(ChatFormatting.DARK_GRAY)
                ));
            } else {
                appendHoldCtrlHint(event);
            }
            return;
        }
        PoleKey master = bound.master();
        event.getTooltipElements().add(Either.left(indent(Component.translatable(
            "tooltip.anvilcraft_pullwires.item.bound",
            Component.literal(master.pos().getX() + ", " + master.pos().getY() + ", " + master.pos().getZ())
                .withStyle(ChatFormatting.YELLOW),
            Component.translatable("dimension." + master.dimension().location().toLanguageKey())
                .withStyle(ChatFormatting.GRAY)
        ).withStyle(ChatFormatting.GOLD))));

        if (!details) {
            appendHoldCtrlHint(event);
            return;
        }

        // 优先级与锁定属于「被绑定的那根杆」，从客户端镜像里读——服务端每拍同步。
        PoleConfig config = ManualLinkClientState.configOf(master);
        event.getTooltipElements().add(Either.left(indent(Component.translatable(
            "tooltip.anvilcraft_pullwires.link.priority",
            Component.literal(String.valueOf(config.priority())).withStyle(ChatFormatting.YELLOW)
        ).withStyle(ChatFormatting.GRAY))));
        if (config.locked()) {
            event.getTooltipElements().add(Either.left(
                Component.translatable("tooltip.anvilcraft_pullwires.locked").withStyle(ChatFormatting.RED)
            ));
        }
    }

    /** 「按住 [左 Ctrl] 查看绑定细节」——键名走语言键，不要写字面量。 */
    private static void appendHoldCtrlHint(RenderTooltipEvent.GatherComponents event) {
        event.getTooltipElements().add(Either.left(
            Component.translatable(
                "tooltip.anvilcraft_pullwires.item.hold_ctrl",
                Component.translatable("tooltip.anvilcraft_pullwires.item.key_ctrl").withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.DARK_GRAY)
        ));
    }

    private static MutableComponent line(String key, int value) {
        return indent(Component.translatable(
            "tooltip.anvilcraft_pullwires." + key,
            Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.GRAY));
    }

    /** 与本体 tooltip 一致的缩进。 */
    private static MutableComponent indent(Component line) {
        return ITooltipProvider.INDENTATION.copy().append(line);
    }
}
