package dev.anvilcraft.pullwires.client;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.TransmissionPoleBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件入口：把 {@link PoleInfoProvider} 挂到四种杆上。
 *
 * <p>Jade 是可选的（{@code neoforge.mods.toml} 里声明为 optional），
 * 因此这个类只会被 <b>Jade 自己</b>扫描到——没装 Jade 时它根本不会被加载，
 * 也就不需要任何 {@code isLoaded} 守卫。</p>
 *
 * <h2>只登记两个父类，否则浮层会出现两遍</h2>
 * <p>Jade 的匹配是「登记的类型能从目标方块类赋值而来」，也就是<b>按继承匹配</b>。
 * 一开始我把四种杆都登记了一遍，而我们那两种又继承自 {@code RemoteTransmissionPoleBlock}——
 * 于是超远程杆 / 跨维度杆同时命中父类与子类两条登记，同一个 provider 被追加了两次，
 * 界面上就是「同一块 [拉电线] 内容显示两遍」。</p>
 *
 * <p>改成只登记 {@code TransmissionPoleBlock} 与 {@code RemoteTransmissionPoleBlock}：
 * 本体的两种杆各命中一次，我们自己那两种作为子类命中各自的父类一次。</p>
 */
@WailaPlugin(AnvilCraftPullWires.MOD_ID)
public class AddonJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(PoleInfoProvider.INSTANCE, TransmissionPoleBlock.class);
        registration.registerBlockComponent(PoleInfoProvider.INSTANCE, RemoteTransmissionPoleBlock.class);
    }
}
