package dev.anvilcraft.pullwires.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让本模组的方块也能进入本体的「立方体选择 / 描边」快照。
 *
 * <h2>为什么需要这个注入</h2>
 * <p>多方块放置预览的<b>线框模式</b>依赖 {@code ModelBlockSelection.multipartOutline(state)}，
 * 而它的数据来自 {@code ModelSelectionBakery#bake} 烘出来的快照。该方法第一行就是：</p>
 * <pre>
 * for (Block block : BuiltInRegistries.BLOCK) {
 *     if (!AnvilCraft.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) continue;
 *     ...
 * }
 * </pre>
 * <p>目标是<b>包私有</b>类，所以这里必须用字符串形式的 	argets，否则编译期根本引不到它。
 * <p>也就是<b>只处理 {@code anvilcraft} 命名空间</b>。附属模组的方块在这一步就被跳过，
 * 快照里查不到它们的状态，线框渲染器拿不到描边就返回 false，于是预览一律退化成虚影。</p>
 *
 * <h2>注入点为什么选这个 equals</h2>
 * <p>{@code bake} 方法里共有三处 {@code String.equals}，源码顺序如下：</p>
 * <ol>
 *   <li>命名空间过滤 —— <b>要改的就是它</b>（{@code ordinal = 0}）；</li>
 *   <li>{@code STANDALONE_VARIANT.equals(variant)}；</li>
 *   <li>第二圈循环里的命名空间过滤。</li>
 * </ol>
 * <p>只命中第一处即可：把「命名空间必须等于 {@code anvilcraft}」放宽为「等于 {@code anvilcraft}
 * 或本模组命名空间」。用 {@code require = 0} 是刻意的——本体将来重写这段逻辑时，
 * 这里静默失效只会退回到「只有虚影」，不会让游戏起不来。</p>
 */
@Mixin(targets = "dev.dubhe.anvilcraft.client.selection.ModelSelectionBakery")
public abstract class ModelSelectionBakeryMixin {
    /** 本模组的命名空间，与 {@code AnvilCraftPullWires.MOD_ID} 保持一致。 */
    private static final String PULLWIRES_NAMESPACE = "anvilcraft_pullwires";

    @Redirect(
        method = "bake",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", ordinal = 0),
        require = 0
    )
    private static boolean pullwires$alsoBakeOwnNamespace(String self, Object other) {
        return self.equals(other) || PULLWIRES_NAMESPACE.equals(other);
    }
}
