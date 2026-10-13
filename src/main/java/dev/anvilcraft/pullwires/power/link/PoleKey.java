package dev.anvilcraft.pullwires.power.link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 一根输电杆在世界中的唯一标识：维度 + 方块坐标。
 *
 * <p>本模组的全部链路数据都以它为键存放，因此<b>不需要侵入本体的方块实体</b>——
 * 本体的 {@code TransmissionPoleBlockEntity} 没有可扩展字段，
 * 而 {@code PowerGrid.findPowerGridContains} 是公开 API，仅凭坐标就能解析出杆所属的电网。</p>
 */
public record PoleKey(ResourceKey<Level> dimension, BlockPos pos) implements Comparable<PoleKey> {
    public static final Codec<PoleKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PoleKey::dimension),
        BlockPos.CODEC.fieldOf("pos").forGetter(PoleKey::pos)
    ).apply(instance, PoleKey::new));

    public static PoleKey of(Level level, BlockPos pos) {
        return new PoleKey(level.dimension(), pos.immutable());
    }

    @Override
    public int compareTo(PoleKey other) {
        int byDimension = this.dimension.location().compareTo(other.dimension.location());
        return byDimension != 0 ? byDimension : this.pos.compareTo(other.pos);
    }
}
