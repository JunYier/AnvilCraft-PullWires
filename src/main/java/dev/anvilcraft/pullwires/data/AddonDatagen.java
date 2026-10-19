package dev.anvilcraft.pullwires.data;

import static dev.anvilcraft.pullwires.AnvilCraftPullWires.REGISTRUM;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.anvilcraft.pullwires.data.lang.AddonLangHandler;

/**
 * 数据生成入口：目前只有语言文件。
 *
 * <p>具体的数据生成交给 Registrum：{@link #init()} 把 {@link AddonLangHandler} 挂到
 * {@code ProviderType.LANG} 上，Registrum 自己监听 {@code GatherDataEvent} 并驱动它们，
 * 因此本类<b>不需要</b>再自带一个空的 {@code @SubscribeEvent} 处理器。</p>
 */
public final class AddonDatagen {
    private AddonDatagen() {
    }

    /** 初始化生成器；由 {@code AnvilCraftPullWires} 的构造调用，必须早于数据生成事件。 */
    public static void init() {
        REGISTRUM.addDataGenerator(ProviderType.LANG, AddonLangHandler::init);
    }
}
