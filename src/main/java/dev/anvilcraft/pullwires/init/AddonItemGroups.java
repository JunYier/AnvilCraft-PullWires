package dev.anvilcraft.pullwires.init;

import static dev.anvilcraft.pullwires.AnvilCraftPullWires.REGISTRUM;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组自己的创造模式标签页。
 *
 * <h2>为什么不挤进本体的页</h2>
 * <p>一开始的目标是「插到本体两根输电杆后面」，实际上走了三条路，都撞在同一个东西上——
 * 本体的标签页用的是 <b>AnvilLib 的小分类体系</b>
 * （{@code CreativeTabSections.build(tabId, ...)}），而它的分类归属由<b>谁跑这个标签页的生成器</b>决定，
 * 并且每个标签页的布局是 {@code LAYOUTS.put(tabId, ...)} <b>整份覆盖、不合并</b>。
 * 所以外挂模组：</p>
 * <ul>
 *   <li>用 {@code insertAfter} 插——锚点不在 NeoForge 的默认填充流程里，插不进去；</li>
 *   <li>用 Registrum 的 {@code defaultCreativeTab} 指过去——物品确实进了那一页，
 *       但不在任何被记录的分类区间内，于是被 {@code arrange} 排到<b>整页最后一行</b>。</li>
 * </ul>
 * <p>与其停在一个「看得见但位置不对」的中间态，不如给本模组一个自己的页：
 * 位置确定、内容可控，也不会随本体改分类实现而漂移。</p>
 *
 * <h2>displayItems 必须<b>留空</b>（V1.12.13 修正）</h2>
 * <p>这里踩过一次崩溃，记下来：Registrum
 * （{@code AbstractRegistrum#onBuildCreativeModeTabContents}）<b>自己会监听
 * {@code BuildCreativeModeTabContentsEvent}</b>，把「声明了某个标签页」的条目填进去。
 * 所以我一度写实的 {@code displayItems} 等于<b>把同一件物品加了两次</b>，NeoForge 当场抛
 * {@code IllegalArgumentException: Itemstack ... already exists in the tab's list}，游戏起不来。</p>
 *
 * <p>结论：<b>标签页内容由 Registrum 独占填充</b>，本类只负责「注册这一页」；
 * 物品归属由 {@code AddonBlocks} 里的 {@code REGISTRUM.defaultCreativeTab(...)} 声明。
 * 两边都做 = 重复；只做 Registrum 那边 = 正确。</p>
 *
 * <p>我当初改成写实的理由是「担心自动填充时机不对、页面是空的」——那个担心不成立，
 * 代价却是一次进不了游戏的崩溃。宁可相信注册系统既有的机制。</p>
 */
public final class AddonItemGroups {
    private static final DeferredRegister<CreativeModeTab> DEFERRED_REGISTER = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB,
        AnvilCraftPullWires.MOD_ID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ADDON_ITEMS = DEFERRED_REGISTER.register(
        "addon_items",
        () -> CreativeModeTab.builder()
            .icon(() -> AddonBlocks.ULTRA_LONG_RANGE_POLE.asStack())
            .title(REGISTRUM.addLang("itemGroup", AnvilCraftPullWires.of("addon_items"), "AnvilCraft: Pull Wires"))
            // 刻意留空：见类文档。物品由 Registrum 依 AddonBlocks 里的 defaultCreativeTab 声明填充；
            // 这里再 accept 一次，同一物品就会在一页里出现两次并抛异常。
            .displayItems((parameters, output) -> {
            })
            // 排在本体的功能方块页之后，玩家找完本体那两种杆顺手就能看到我们的两种。
            .withTabsAfter(dev.dubhe.anvilcraft.init.item.ModItemGroups.ANVILCRAFT_FUNCTION_BLOCK.getId())
            .build()
    );

    private AddonItemGroups() {
    }

    public static void register(IEventBus modEventBus) {
        DEFERRED_REGISTER.register(modEventBus);
    }
}
