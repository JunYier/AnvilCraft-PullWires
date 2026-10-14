package dev.anvilcraft.pullwires.power.link;

import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * 手持物上的「绑定主端」。
 *
 * <p>这是本模组绑定机制的唯一载体：<b>无论是手持电线杆还是手持磁盘，绑定都写在这里</b>。
 * 服务端不再维护任何「谁在待连接」的状态表——那正是「背包里所有同类杆都被附上光泽」
 * 「丢出去／创造模式删除后状态还挂着」「虚空连接」这一串 bug 的同源成因：
 * 服务端状态与「玩家手上拿什么」是分离的。</p>
 *
 * <p>数据写在自定义数据的独立键 {@code pullwires_binding} 下，与本体磁盘的方块数据互不干扰；
 * 因此本体磁盘的普通复制 / 粘贴不会带走绑定关系。</p>
 *
 * <p>对齐本体定日镜 {@code HeliostatsItem} 的做法（绑定数据存在物品组件上）。</p>
 */
public final class LinkBinding {
    private static final String KEY = "pullwires_binding";

    private LinkBinding() {
    }

    /**
     * 该物品能不能承载绑定。
     *
     * <p>目前是四种电线杆物品与磁盘。判据刻意<b>不</b>要求「已经绑定」——
     * 绑定动作本身就要往空物品上写。</p>
     */
    public static boolean canBind(ItemStack stack) {
        return PoleKind.isPoleItem(stack) || stack.getItem() instanceof DiskItem;
    }

    /** 把绑定写到该物品上。不满足 {@link #canBind} 时静默忽略。 */
    public static void bind(ItemStack stack, PoleKey key) {
        if (!canBind(stack)) return;
        Tag encoded = PoleKey.CODEC.encodeStart(NbtOps.INSTANCE, key).result().orElse(null);
        if (encoded == null) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(KEY, encoded);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 读取绑定的主端；未绑定时返回 null。 */
    public static @Nullable PoleKey read(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Tag encoded = tag.get(KEY);
        if (encoded == null) return null;
        return PoleKey.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
    }

    public static void clear(ItemStack stack) {
        if (stack.isEmpty()) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(KEY)) return;
        tag.remove(KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

}