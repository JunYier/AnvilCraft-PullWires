package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.network.AdjustPriorityPacket;
import dev.anvilcraft.pullwires.network.SetReconnectSortPacket;
import dev.anvilcraft.pullwires.power.link.PoleConfig;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PrioritySliderMath;
import dev.anvilcraft.pullwires.power.link.ReconnectPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * 一根输电杆专属的「重连申请排序 + 优先级」配置页。
 *
 * <h2>页面上的四件事</h2>
 * <ol>
 *   <li><b>优先级</b>：一个 0–15 的滑条，<b>松手时</b>才发一次增量 → {@link AdjustPriorityPacket}；</li>
 *   <li><b>首要因素</b>：一个按钮在「优先级 / 需求」之间切换 → {@code ReconnectPolicy.primary}；</li>
 *   <li><b>主因素方向</b>：一个按钮在升序 / 降序之间切换 → {@code primaryAscending}；</li>
 *   <li><b>副因素方向</b>：一个按钮在升序 / 降序之间切换 → {@code secondaryAscending}
 *       （副因素自动是「主因素之外的那一个」，所以没有第二个「谁是副因素」的按钮）。</li>
 * </ol>
 *
 * <p><b>时间因素</b>只显示、<b>没有开关</b>——{@code ReconnectPolicy.compare} 里它就是固定升序。</p>
 *
 * <h2>为什么用滑条（V1.12.14）</h2>
 * <p>V1.12.12–V1.12.13 这里放的是 AnvilLib 的径向轮盘（16 扇区 = 0–15）。
 * 轮盘的「指向即选中」会让鼠标移动不断改选中扇区，必须先选再点一下才生效，交互绕；
 * 现在换成一个整数滑条：拖动只移动滑块，<b>松手（{@code onRelease}）才提交一次</b>，
 * 拖动过程中不会每帧发包。滑条本身没有采用本体的 {@code dev.dubhe.anvilcraft...Slider}——
 * 它的 {@code getValue()} 会把比例再按 {@code 2^(v-1)} 做一次指数变换，并不适合 0–15 的线性整数，
 * 因此用原版 {@link AbstractSliderButton}，映射规则抽成可脱离游戏断言的
 * {@link PrioritySliderMath}。</p>
 *
 * <h2>没有第二份状态</h2>
 * <p>页面上显示的优先级文字实时读自 {@link ManualLinkClientState}——服务端同步下来的只读镜像。
 * 滑块位置会在不拖动时跟随镜像；写入只发一个包，<b>不做乐观更新</b>：
 * 松手后等下一份 {@code SyncRegistryPacket} 回来，文字与滑块自然对齐。
 * 本类持有的字段只有 {@link #key}（这是哪根杆）与 {@link #parent}（从哪来）。</p>
 */
public class PoleReconnectSortScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int MAX_BUTTON_ROW_WIDTH = 300;
    private static final int MAX_SLIDER_WIDTH = 220;
    private static final int LABEL_GAP = 4;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFFA0A0A0;

    private final PoleKey key;
    private final @Nullable Screen parent;

    private @Nullable PrioritySlider prioritySlider;
    private @Nullable Button factorButton;
    private @Nullable Button primaryButton;
    private @Nullable Button secondaryButton;

    public PoleReconnectSortScreen(PoleKey key, @Nullable Screen parent) {
        super(Component.translatable("screen.anvilcraft_pullwires.pole_sort.title"));
        this.key = key;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        int sliderWidth = Math.min(MAX_SLIDER_WIDTH, this.width - 40);
        int sliderY = this.height / 2 - 10;
        this.prioritySlider = this.addRenderableWidget(new PrioritySlider(
            centerX - sliderWidth / 2, sliderY, sliderWidth, BUTTON_HEIGHT
        ));

        int rowWidth = Math.min(MAX_BUTTON_ROW_WIDTH, this.width - 20);
        int buttonWidth = (rowWidth - 2 * BUTTON_GAP) / 3;
        int rowLeft = centerX - rowWidth / 2;
        int togglesY = this.height - BUTTON_HEIGHT - 8;

        this.factorButton = this.addRenderableWidget(Button
            .builder(Component.empty(), button -> this.toggleFactor())
            .size(buttonWidth, BUTTON_HEIGHT)
            .pos(rowLeft, togglesY)
            .build());
        this.primaryButton = this.addRenderableWidget(Button
            .builder(Component.empty(), button -> this.togglePrimaryDirection())
            .size(buttonWidth, BUTTON_HEIGHT)
            .pos(rowLeft + buttonWidth + BUTTON_GAP, togglesY)
            .build());
        this.secondaryButton = this.addRenderableWidget(Button
            .builder(Component.empty(), button -> this.toggleSecondaryDirection())
            .size(buttonWidth, BUTTON_HEIGHT)
            .pos(rowLeft + 2 * (buttonWidth + BUTTON_GAP), togglesY)
            .build());
    }

    // ---------------------------------------------------------------- 读写

    /** 页面上所有数值的唯一来源：服务端同步下来的只读镜像。 */
    private PoleConfig config() {
        return ManualLinkClientState.configOf(this.key);
    }

    /** 本杆实际生效的策略（它自己那一份；尚未补齐时退回存档默认）。 */
    private ReconnectPolicy policy() {
        return ManualLinkClientState.policyOf(this.key);
    }

    private void toggleFactor() {
        this.write(this.policy().toggledPrimary());
    }

    private void togglePrimaryDirection() {
        this.write(this.policy().toggledPrimaryAscending());
    }

    private void toggleSecondaryDirection() {
        this.write(this.policy().toggledSecondaryAscending());
    }

    /**
     * 只把「三个开关」发给服务端；策略由服务端用权威数据拼，并且直接写进<b>这根杆自己</b>的配置。
     *
     * <p>起点是 {@link #policy()}（当前生效值）：从「还没补齐、显示的是存档默认」这一状态按下按钮时，
     * 写下去的也是「默认值 + 这一下改动」，玩家看到的顺序不会被莫名其妙地换掉。</p>
     */
    private void write(ReconnectPolicy policy) {
        PacketDistributor.sendToServer(new SetReconnectSortPacket(
            this.key.pos(),
            policy.demandFirst(),
            policy.primaryAscending(),
            policy.secondaryAscending()
        ));
    }

    /** 滑条松手时把镜像差多少发下去；真正的夹取仍由服务端在权威配置上做。 */
    private void commitPriority(int target) {
        int delta = PrioritySliderMath.deltaFor(target, this.config().priority());
        if (delta == 0) return;
        PacketDistributor.sendToServer(new AdjustPriorityPacket(this.key.pos(), delta));
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.refreshWidgets();
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        ReconnectPolicy policy = this.policy();
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, TEXT_COLOR);
        guiGraphics.drawCenteredString(
            this.font,
            Component.translatable(
                "screen.anvilcraft_pullwires.pole_sort.roles",
                factorName(policy.primary()),
                factorName(policy.secondary())
            ),
            this.width / 2, 24, HINT_COLOR
        );
        guiGraphics.drawCenteredString(
            this.font,
            Component.translatable("screen.anvilcraft_pullwires.pole_sort.time"),
            this.width / 2, 36, HINT_COLOR
        );

        if (this.prioritySlider != null) {
            // 滑条自身的 message 显示镜像值；这一行是给它的可读标签。
            guiGraphics.drawCenteredString(
                this.font,
                Component.translatable("screen.anvilcraft_pullwires.pole_sort.label.priority"),
                this.prioritySlider.getX() + this.prioritySlider.getWidth() / 2,
                this.prioritySlider.getY() - this.font.lineHeight - LABEL_GAP,
                TEXT_COLOR
            );
        }
        this.drawButtonLabel(guiGraphics, this.factorButton, "screen.anvilcraft_pullwires.pole_sort.label.factor");
        this.drawButtonLabel(guiGraphics, this.primaryButton, "screen.anvilcraft_pullwires.pole_sort.label.primary");
        this.drawButtonLabel(guiGraphics, this.secondaryButton, "screen.anvilcraft_pullwires.pole_sort.label.secondary");
    }

    /** 在按钮正上方画一行小标签；按钮自身继续显示当前取值。 */
    private void drawButtonLabel(GuiGraphics guiGraphics, @Nullable Button button, String translationKey) {
        if (button == null) return;
        guiGraphics.drawCenteredString(
            this.font,
            Component.translatable(translationKey),
            button.getX() + button.getWidth() / 2,
            button.getY() - this.font.lineHeight - LABEL_GAP,
            HINT_COLOR
        );
    }

    /** 按钮文案与滑条位置都跟着镜像走；三个开关始终可用——按下去就是直接改这根杆自己的设定。 */
    private void refreshWidgets() {
        ReconnectPolicy policy = this.policy();
        int priority = this.config().priority();

        if (this.prioritySlider != null) {
            this.prioritySlider.syncFromMirror(priority);
        }
        if (this.factorButton != null) {
            this.factorButton.setMessage(Component.translatable(
                "screen.anvilcraft_pullwires.pole_sort.factor",
                factorName(policy.primary())
            ));
        }
        if (this.primaryButton != null) {
            this.primaryButton.setMessage(Component.translatable(policy.primaryAscending()
                ? "screen.anvilcraft_pullwires.pole_sort.primary.asc"
                : "screen.anvilcraft_pullwires.pole_sort.primary.desc"));
        }
        if (this.secondaryButton != null) {
            this.secondaryButton.setMessage(Component.translatable(policy.secondaryAscending()
                ? "screen.anvilcraft_pullwires.pole_sort.secondary.asc"
                : "screen.anvilcraft_pullwires.pole_sort.secondary.desc"));
        }
    }

    private static Component factorName(ReconnectPolicy.Factor factor) {
        return Component.translatable(factor == ReconnectPolicy.Factor.DEMAND
            ? "screen.anvilcraft_pullwires.pole_sort.factor.demand"
            : "screen.anvilcraft_pullwires.pole_sort.factor.priority");
    }

    @Override
    public boolean isPauseScreen() {
        // 不暂停：页面开着的时候服务端还在每拍同步，关闭后要立刻看到写入的结果。
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    /**
     * 0–15 的整数滑条。
     *
     * <p>拖动过程中 {@link #applyValue()} 什么都不做——只有 {@link #onRelease(double, double)}
     * 才把「滑块目标值 − 镜像值」的增量发出去一次。页面上的文字始终读镜像，
     * 因此滑条的位置状态只是输入过程中的临时位置，不是第二份权威数据。</p>
     */
    private final class PrioritySlider extends AbstractSliderButton {
        /** 这一次按下是否归我们处理；松手后置回 false。 */
        private boolean dragging;

        private PrioritySlider(int x, int y, int width, int height) {
            super(
                x, y, width, height,
                Component.empty(),
                PrioritySliderMath.proportionOf(PoleReconnectSortScreen.this.config().priority())
            );
            this.updateMessage();
        }

        /** 滑块当前指向的整数优先级。 */
        private int selectedPriority() {
            return PrioritySliderMath.priorityAt(this.value);
        }

        /** 不拖动时跟随服务端镜像；标签无论如何都显示镜像值。 */
        private void syncFromMirror(int priority) {
            if (!this.dragging) {
                double proportion = PrioritySliderMath.proportionOf(priority);
                if (Math.abs(this.value - proportion) > 1.0E-6D) {
                    this.value = proportion;
                }
            }
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(
                "screen.anvilcraft_pullwires.pole_sort.priority.value",
                Component.literal(String.valueOf(PoleReconnectSortScreen.this.config().priority()))
            ));
        }

        @Override
        protected void applyValue() {
            // 拖动中不发包：只在松手时提交一次增量。
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            if (handled) this.dragging = true;
            return handled;
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            if (!this.dragging) return;
            this.dragging = false;
            PoleReconnectSortScreen.this.commitPriority(this.selectedPriority());
        }
    }
}
