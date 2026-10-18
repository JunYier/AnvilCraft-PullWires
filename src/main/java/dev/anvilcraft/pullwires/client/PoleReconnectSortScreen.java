package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.network.AdjustPriorityPacket;
import dev.anvilcraft.pullwires.network.SetReconnectSortPacket;
import dev.anvilcraft.pullwires.power.link.PoleConfig;
import dev.anvilcraft.pullwires.power.link.PoleKey;
import dev.anvilcraft.pullwires.power.link.PolePriority;
import dev.anvilcraft.pullwires.power.link.ReconnectPolicy;
import dev.anvilcraft.lib.v2.wheel.client.gui.component.WheelWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 一根输电杆专属的「重连申请排序」配置页。
 *
 * <h2>页面上的三件事</h2>
 * <ol>
 *   <li><b>首要因素</b>：一个按钮在「优先级 / 需求」之间切换 → {@code ReconnectPolicy.primary}；</li>
 *   <li><b>主因素方向</b>：一个按钮在升序 / 降序之间切换 → {@code primaryAscending}；</li>
 *   <li><b>副因素方向</b>：一个按钮在升序 / 降序之间切换 → {@code secondaryAscending}
 *       （副因素自动是「主因素之外的那一个」，所以没有第二个「谁是副因素」的按钮）；</li>
 *   <li><b>时间因素</b>：只显示、<b>没有开关</b>——{@code ReconnectPolicy.compare} 里它就是固定升序。</li>
 * </ol>
 *
 * <p><b>没有「排序来源」开关（V1.12.13 删除）</b>：页面上按下的任何一下都<b>直接写进这根杆自己</b>的
 * 排序设定，不存在「本杆覆盖 ⇄ 跟随存档默认」这个中间概念。存档级默认只在进世界补齐时，
 * 作为一份快照被复制给<b>尚未配置</b>的杆（{@code PowerInterconnectManager} 的每拍结算里，
 * 见 {@code WireLinkRegistry.backfillReconnectPolicies}）；已经配置过的杆一律跳过。</p>
 *
 * <h2>优先级轮盘（原「Ctrl + 滚轮」并入这里）</h2>
 * <p>页面中央的径向轮盘是 AnvilLib wheel 的 {@link WheelWidget}，16 个扇区就是 0–15。
 * 指向某个扇区、或滚动滚轮，都会把<b>选择</b>移过去；<b>左键点击盘面</b>才真正写下去。
 * 之所以不做成「滚到哪就改到哪」：轮盘本身会随鼠标方向不断改变选中扇区，
 * 那样光是移动鼠标就会把优先级刷成一片乱数。</p>
 *
 * <h2>没有第二份状态</h2>
 * <p>页面上显示的每一个数都实时读自 {@link ManualLinkClientState}——服务端同步下来的只读镜像。
 * 这里既不缓存「当前策略」，也不做乐观更新：写完只发一个包，等下一份 {@code SyncRegistryPacket}
 * 回来自然就对了。本类持有的字段只有 {@link #key}（这是哪根杆）与 {@link #parent}（从哪来）。</p>
 */
public class PoleReconnectSortScreen extends Screen {
    /** 轮盘中心多大范围内算「死区」（不选中任何扇区）。 */
    private static final int WHEEL_DEAD_ZONE = 30;
    private static final float WHEEL_OUTER_SCALE = 0.26F;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int MAX_BUTTON_ROW_WIDTH = 300;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFFA0A0A0;

    private final PoleKey key;
    private final @Nullable Screen parent;

    private @Nullable WheelWidget wheel;
    private @Nullable Button factorButton;
    private @Nullable Button primaryButton;
    private @Nullable Button secondaryButton;
    private float wheelOuterRadius = 40.0F;

    public PoleReconnectSortScreen(PoleKey key, @Nullable Screen parent) {
        super(Component.translatable("screen.anvilcraft_pullwires.pole_sort.title"));
        this.key = key;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        float limit = Math.min(this.width, this.height) * WHEEL_OUTER_SCALE;
        // 上下都要留位置：上面两行说明、下面一行按钮（V1.12.13 起只剩这一行）。
        this.wheelOuterRadius = Math.max(40.0F, Math.min(limit, this.height / 2.0F - 40.0F));
        float innerRadius = Math.max(20.0F, this.wheelOuterRadius * 0.45F);

        this.wheel = new WheelWidget(
            0, 0, this.width, this.height,
            innerRadius, this.wheelOuterRadius,
            prioritySections(), WHEEL_DEAD_ZONE
        );

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

    /** 16 个扇区：数值 0–15，扇区里直接把数字画出来（否则整圈只有中心标题有字）。 */
    private List<WheelWidget.RawSection> prioritySections() {
        List<WheelWidget.RawSection> sections = new ArrayList<>(PolePriority.MAX - PolePriority.MIN + 1);
        for (int value = PolePriority.MIN; value <= PolePriority.MAX; value++) {
            Component label = Component.literal(String.valueOf(value));
            sections.add(new WheelWidget.RawSection(
                label,
                (graphics, pose, sectionWidth, sectionHeight) -> graphics.drawCenteredString(
                    this.font, label, 0, -this.font.lineHeight / 2, TEXT_COLOR
                )
            ));
        }
        return sections;
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

    /**
     * 把轮盘选中的优先级写下去。
     *
     * <p>发的是<b>增量</b>而不是绝对值：数值仍旧由服务端在权威配置上夹取，
     * 客户端算出来的只是「相对镜像差多少」，镜像过期最多导致一次空操作，不会写出越界值。</p>
     */
    private void applyPriority(int value) {
        int delta = value - this.config().priority();
        if (delta == 0) return;
        PacketDistributor.sendToServer(new AdjustPriorityPacket(this.key.pos(), delta));
    }

    // ---------------------------------------------------------------- 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让按钮拿到点击，再考虑轮盘——轮盘的 bounds 是整个屏幕，先问它会吞掉所有按钮。
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || this.wheel == null) return false;
        if (!this.insideWheelRing(mouseX, mouseY)) return false;

        int selected = this.wheel.getCurrentSectionIndex();
        if (selected < 0) return false;
        this.applyPriority(selected);
        return true;
    }

    /**
     * 滚轮只改变轮盘的<b>选择</b>，不直接写入。
     *
     * <p>这就是原「Ctrl + 滚轮」在页面里的落点：同一个手势，但现在作用在一个看得见的轮盘上，
     * 并且要再点一下才算数。世界上那条滚轮通路已经删除，不存在第二套入口。</p>
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.wheel != null && scrollY != 0) {
            this.wheel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** 点是否落在轮盘的环带上（死区与盘外都不算）。 */
    private boolean insideWheelRing(double mouseX, double mouseY) {
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance >= WHEEL_DEAD_ZONE && distance <= this.wheelOuterRadius;
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.refreshButtons();
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        if (this.wheel != null) {
            // render 里会按鼠标方向更新选中扇区，所以必须每帧调用。
            this.wheel.render(guiGraphics, mouseX, mouseY, partialTick);
        }
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
        guiGraphics.drawCenteredString(
            this.font,
            Component.translatable(
                "screen.anvilcraft_pullwires.pole_sort.priority",
                Component.literal(String.valueOf(this.config().priority()))
            ),
            this.width / 2, 48, HINT_COLOR
        );
    }

    /** 按钮文案跟着镜像走；三个开关始终可用——按下去就是直接改这根杆自己的设定。 */
    private void refreshButtons() {
        ReconnectPolicy policy = this.policy();

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
}
