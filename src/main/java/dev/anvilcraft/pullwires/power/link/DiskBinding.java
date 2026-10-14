package dev.anvilcraft.pullwires.power.link;

import com.mojang.serialization.Codec;
import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * 磁盘上的「绑定主端」。
 *
 * <p><b>刻意不放进本体的 {@code IDiskCloneable} 数据里。</b>那是给「单个方块的配置」用的，
 * 而绑定关系是「杆与杆之间的关系」——塞进去会让「复制一根杆的配置」顺手把绑定也带走，
 * 违反「磁盘复制粘贴不会一并复制绑定关系」这条需求。</p>
 *
 * <p>因此绑定写在自定义数据的独立键 {@code pullwires_binding} 下，与本体数据互不干扰；
 * 只有按住热键右键才会写入，本体磁盘的普通复制 / 粘贴不会碰到它。</p>
 */
public final class DiskBinding {
    private static final String KEY = "pullwires_binding";

    private DiskBinding() {
    }

    /** 把该磁盘绑定到某根主端杆；传入的不是磁盘时静默忽略。 */
    public static void bind(ItemStack stack, PoleKey key) {
        if (!(stack.getItem() instanceof DiskItem)) return;
        Tag encoded = PoleKey.CODEC.encodeStart(NbtOps.INSTANCE, key).result().orElse(null);
        if (encoded == null) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove("pullwires_disk_meta_unused");
        tag.put(KEY, encoded);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 读取绑定的主端；未绑定时返回 null。 */
    public static @Nullable PoleKey read(ItemStack stack) {
        if (!(stack.getItem() instanceof DiskItem)) return null;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Tag encoded = tag.get(KEY);
        if (encoded == null) return null;
        return PoleKey.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
    }

    public static void clear(ItemStack stack) {
        if (!(stack.getItem() instanceof DiskItem)) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(KEY)) return;
        tag.remove(KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
