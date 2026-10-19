package dev.anvilcraft.pullwires.init;

import static dev.anvilcraft.pullwires.AnvilCraftPullWires.REGISTRUM;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.block.CrossDimensionalPoleBlock;
import dev.anvilcraft.pullwires.block.UltraLongRangePoleBlock;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.item.SimpleMultiPartBlockItem;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * 本模组的方块注册。
 *
 * <h2>为什么模型与掉落表走 datagen</h2>
 * <p>这两根杆的多部分 blockstate 与「只在底段掉落」的掉落表都是<b>手工写过一遍</b>的，
 * 但 datagen 每次都会把它们覆盖成错误版本（单变体 blockstate + 无条件掉落表）。
 * 靠「跑完 datagen 再手工贴回去」迟早会忘，所以这里把两者固化成 provider。</p>
 *
 * <p>贴图仍是暂借本体的远程输电桩资源（已复制进本模组命名空间），
 * 换成正式美术时只需要替换 {@code textures/block} 下的 png 与 {@code models/block} 下的 json，
 * 本文件不需要改。</p>
 */
public class AddonBlocks {

    static {
        // 两种杆的归属声明。**这是它们进入创造页的唯一途径**：
        // Registrum 会监听 BuildCreativeModeTabContentsEvent，把声明了这一页的条目填进去。
        // 千万不要再在 AddonItemGroups 的 displayItems 里手动加一遍——那会让同一物品进同一页两次，
        // NeoForge 直接抛「already exists in the tab's list」，游戏起不来（V1.12.13 就是这么崩的）。
        REGISTRUM.defaultCreativeTab(AddonItemGroups.ADDON_ITEMS.getKey());
    }
    private static final String ULTRA = "ultra_long_range_pole";
    private static final String CROSS = "cross_dimensional_pole";

    public static final BlockEntry<UltraLongRangePoleBlock> ULTRA_LONG_RANGE_POLE = REGISTRUM.block(
            ULTRA,
            UltraLongRangePoleBlock::new
        )
        .properties(properties -> properties.strength(2.0F).isValidSpawn(Blocks::never).noOcclusion())
        .blockstate((context, provider) -> {
            var builder = provider.getVariantBuilder(context.get());
            for (Vertical4PartHalf half : Vertical4PartHalf.values()) {
                for (boolean overload : new boolean[] {false, true}) {
                    for (IPowerComponent.Switch powerSwitch : IPowerComponent.Switch.values()) {
                        builder.partialState()
                            .with(RemoteTransmissionPoleBlock.HALF, half)
                            .with(IPowerComponent.OVERLOAD, overload)
                            .with(RemoteTransmissionPoleBlock.SWITCH, powerSwitch)
                            .modelForState()
                            .modelFile(provider.models().getExistingFile(poleModel("ultra_long_range_pole_head", half, overload, powerSwitch)))
                            .addModel();
                    }
                }
            }
        })
        .loot((tables, block) -> tables.add(block, bottomOnly(block)))
        .recipe((context, provider) -> ShapedRecipeBuilder.shaped(
                RecipeCategory.TRANSPORTATION, context.getEntry().asItem()
            )
            .define('F', ModItems.FROST_METAL_INGOT)
            .define('M', ModItems.MAGNET_INGOT)
            .define('P', ModBlocks.REMOTE_TRANSMISSION_POLE)
            .pattern("FMF")
            .pattern("MPM")
            .pattern("FMF")
            .unlockedBy("has_remote_transmission_pole", hasRemotePole())
            .save(provider))
        .item(SimpleMultiPartBlockItem<Vertical4PartHalf>::new)
        .build()
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .register();

    public static final BlockEntry<CrossDimensionalPoleBlock> CROSS_DIMENSIONAL_POLE = REGISTRUM.block(
            CROSS,
            CrossDimensionalPoleBlock::new
        )
        .properties(properties -> properties.strength(2.0F).isValidSpawn(Blocks::never).noOcclusion())
        .blockstate((context, provider) -> {
            var builder = provider.getVariantBuilder(context.get());
            for (Vertical4PartHalf half : Vertical4PartHalf.values()) {
                for (boolean overload : new boolean[] {false, true}) {
                    for (IPowerComponent.Switch powerSwitch : IPowerComponent.Switch.values()) {
                        builder.partialState()
                            .with(RemoteTransmissionPoleBlock.HALF, half)
                            .with(IPowerComponent.OVERLOAD, overload)
                            .with(RemoteTransmissionPoleBlock.SWITCH, powerSwitch)
                            .modelForState()
                            .modelFile(provider.models().getExistingFile(poleModel(half, overload, powerSwitch)))
                            .addModel();
                    }
                }
            }
        })
        .loot((tables, block) -> tables.add(block, bottomOnly(block)))
        .recipe((context, provider) -> ShapedRecipeBuilder.shaped(
                RecipeCategory.TRANSPORTATION, context.getEntry().asItem()
            )
            .define('T', ModItems.TRANSCENDIUM_INGOT)
            .define('M', ModItems.MAGNET_INGOT)
            .define('U', ULTRA_LONG_RANGE_POLE)
            .pattern("TMT")
            .pattern("MUM")
            .pattern("TMT")
            .unlockedBy("has_ultra_long_range_pole", InventoryChangeTrigger.TriggerInstance.hasItems(
                ULTRA_LONG_RANGE_POLE.get()
            ))
            .save(provider))
        .item(SimpleMultiPartBlockItem<Vertical4PartHalf>::new)
        .build()
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .register();

    /**
     * 合成配方的解锁条件：拿到本体的远程输电桩。
     *
     * <p>超远程杆是从远程输电桩改出来的，所以用它当前置足够。</p>
     */
    private static Criterion<InventoryChangeTrigger.TriggerInstance> hasRemotePole() {
        return InventoryChangeTrigger.TriggerInstance.hasItems(ModBlocks.REMOTE_TRANSMISSION_POLE.get());
    }

    /** 与本体远程输电桩一致的四段模型选择（顶段沿用本体的那套头）。 */
    private static ResourceLocation poleModel(
        Vertical4PartHalf half, boolean overload, IPowerComponent.Switch powerSwitch
    ) {
        return poleModel(null, half, overload, powerSwitch);
    }

    /**
     * 同上，但顶段可以换成这根杆自己的头部模型。
     *
     * <p><b>本体的状态切换写法</b>（见 {@code remote_transmission_pole_top*}.json）是
     * 「一个完整几何模型 + 两个只覆盖贴图的变体」：{@code ..._top} 是完整模型，
     * {@code ..._top_off} 与 {@code ..._top_overload} 只是 {@code parent} 指向它、
     * 再把 {@code head} 与 {@code particle} 两张贴图换成对应状态的那张。
     * 因此这里**只需要按状态换顶段的模型名**，几何全部继承，不必复制三份模型。</p>
     *
     * @param headPrefix 顶段模型前缀（如 {@code ultra_long_range_pole_head}）；
     *                   {@code null} 表示顶段继续用本体的 {@code remote_transmission_pole_top*}
     */
    private static ResourceLocation poleModel(
        String headPrefix, Vertical4PartHalf half, boolean overload, IPowerComponent.Switch powerSwitch
    ) {
        if (headPrefix != null && half == Vertical4PartHalf.TOP) {
            String suffix = powerSwitch == IPowerComponent.Switch.OFF
                ? "_off"
                : overload ? "_overload" : "";
            return AnvilCraftPullWires.of("block/" + headPrefix + suffix);
        }
        String name = switch (half) {
            case BOTTOM -> "base";
            case TOP -> powerSwitch == IPowerComponent.Switch.OFF
                ? "top_off"
                : overload ? "top_overload" : "top";
            default -> "mid";
        };
        return AnvilCraftPullWires.of("block/remote_transmission_pole_" + name);
    }

    /**
     * 只在底段掉落的掉落表。
     *
     * <p>多部分方块拆掉时会逐段触发掉落，没有这个条件就会一次掉四份。</p>
     */
    private static LootTable.Builder bottomOnly(net.minecraft.world.level.block.Block block) {
        return LootTable.lootTable().withPool(
            LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(ExplosionCondition.survivesExplosion())
                .add(LootItem.lootTableItem(block)
                    .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties()
                            .hasProperty(RemoteTransmissionPoleBlock.HALF, Vertical4PartHalf.BOTTOM))))
        );
    }

    public static void register() {
        // 静态初始化即注册。
    }
}