package dev.anvilcraft.pullwires.integration.jei;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.init.AddonBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI 信息页。
 *
 * <h2>为什么不是自定义配方类别</h2>
 * <p>本体（{@code dev.dubhe.anvilcraft.integration.jei.AnvilCraftJeiPlugin}）为它的每一种加工方式都写了
 * 自定义类别，因为它有大量本模组没有的配方类型。本模组的情况相反：两根杆用的就是<b>原版工作台配方</b>，
 * JEI 本来就会展示，再写一个类别是重复造轮子。</p>
 *
 * <p>真正缺的是<b>说明</b>——「不供电 / 不自动组网 / 可跨维度」这类信息从合成表里看不出来。
 * 因此这里用 JEI 现成的信息页机制（{@code addIngredientInfo}），
 * 这也是本体没有用到、但恰好适合本模组的那一半 API。</p>
 *
 * <p>JEI 是可选依赖，这个类只会被 JEI 自己扫描到，没装时不会被加载。</p>
 */
@JeiPlugin
public class AddonJeiPlugin implements IModPlugin {
    /** 与本体一致：uid 用 {@code <modid>:jei_plugin}。 */
    @Override
    public ResourceLocation getPluginUid() {
        return AnvilCraftPullWires.of("jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(
            AddonBlocks.ULTRA_LONG_RANGE_POLE.get().asItem(),
            Component.translatable("jei.anvilcraft_pullwires.ultra_long_range_pole")
        );
        registration.addIngredientInfo(
            AddonBlocks.CROSS_DIMENSIONAL_POLE.get().asItem(),
            Component.translatable("jei.anvilcraft_pullwires.cross_dimensional_pole")
        );
    }
}
