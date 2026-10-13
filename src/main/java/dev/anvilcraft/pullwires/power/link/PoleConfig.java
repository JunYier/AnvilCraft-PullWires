package dev.anvilcraft.pullwires.power.link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 输电杆的玩家配置，<b>会写入存档</b>。
 *
 * @param priority 结算优先级，0–15（见 {@link PolePriority}），越低越慷慨
 * @param locked   是否忽略外部红石信号造成的 {@code SWITCH} 切换
 */
public record PoleConfig(int priority, boolean locked) {
    public static final Codec<PoleConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("priority", PolePriority.DEFAULT).forGetter(PoleConfig::priority),
        Codec.BOOL.optionalFieldOf("locked", false).forGetter(PoleConfig::locked)
    ).apply(instance, PoleConfig::new));

    public PoleConfig {
        priority = PolePriority.clamp(priority);
    }

    public PoleConfig withPriority(int newPriority) {
        return new PoleConfig(PolePriority.clamp(newPriority), this.locked);
    }

    public PoleConfig withLocked(boolean newLocked) {
        return new PoleConfig(this.priority, newLocked);
    }
}
