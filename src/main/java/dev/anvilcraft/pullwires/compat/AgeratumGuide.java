package dev.anvilcraft.pullwires.compat;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.resource.ageratum.Ageratum;
import net.minecraft.server.level.ServerPlayer;

/**
 * 对藿香（Ageratum）的可选集成。
 *
 * <h2>为什么单独一个类</h2>
 * <p>藿香在 {@code neoforge.mods.toml} 里声明为 {@code optional}。可选依赖必须有「没装也能跑」的保证，
 * 而「在调用点用 {@code ModList.isLoaded} 包一下」并不足够——只要承载调用的那个类被加载，
 * 验证阶段就可能去解析一个运行时不存在的类型。所以把<b>所有引用藿香类型</b>的代码集中到本类，
 * 只有确认藿香已加载之后才碰它。</p>
 *
 * <h2>传进去的是「文件名」，不是完整资源路径</h2>
 * <p>这是踩过的坑：{@code openGuide} 的第二个参数会被当成
 * {@code GuideDocumentLoader#resolveExistingLocation} 的 <b>fileArgument</b>，
 * 由藿香自己拼成 {@code assets/<命名空间>/ageratum/<语言>/<fileArgument>.md}
 * 并负责「当前语言 → en_us」的回退。</p>
 *
 * <p>所以正确写法是 {@code anvilcraft_pullwires:index}。一开始传的是
 * {@code anvilcraft_pullwires:ageratum/zh_cn/index.md}，被拼成
 * {@code ageratum/<语言>/ageratum/zh_cn/index.md.md}，当然找不到——
 * 而藿香在找不到时是<b>静默返回 false</b>，于是表现为「命令没反应、界面也不出来」。</p>
 */
public final class AgeratumGuide {
    /** 藿香的 modId，同时也是它自己的命名空间。 */
    public static final String MOD_ID = "ageratum";

    /** 手册首页的文件名（不含扩展名）。本体也用同一个值（{@code GuideEventListener.INDEX_FILE}）。 */
    private static final String INDEX = "index";

    private AgeratumGuide() {
    }

    /** 为玩家打开本模组手册的首页；文档不存在时藿香会静默忽略，不抛异常。 */
    public static void open(ServerPlayer player) {
        Ageratum.openGuide(player, AnvilCraftPullWires.of(INDEX));
    }
}
