package dev.anvilcraft.pullwires.power.link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * 一条链路的<b>定义</b>，会写入存档。
 *
 * <p>只记录「谁和谁相连、谁是主端」，不记录任何运行时状态（见 {@link LinkState}）。
 * 这样「触发亏电断路」只是把状态置为 {@link LinkState#SUSPENDED}，
 * 定义仍然保留；开关重启时清空运行时值即可让链路自然恢复，
 * 与「重置所有值，但保留手动连接的值」这条需求完全对应。</p>
 *
 * @param master 主端：管理权归属方（手动连接中＝已放置的那根杆，磁盘连接中＝被"保存"的那根杆）
 * @param slave  从端：连接的发起方（手动连接中＝玩家手上那根杆，磁盘连接中＝被"粘贴"的那根杆）
 */
public record LinkDefinition(UUID id, PoleKey master, PoleKey slave) {
    public static final Codec<LinkDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(LinkDefinition::id),
        PoleKey.CODEC.fieldOf("master").forGetter(LinkDefinition::master),
        PoleKey.CODEC.fieldOf("slave").forGetter(LinkDefinition::slave)
    ).apply(instance, LinkDefinition::new));

    public boolean touches(PoleKey key) {
        return this.master.equals(key) || this.slave.equals(key);
    }

    /** 返回这条链路相对给定端点的另一端；端点不属于该链路时返回 {@code null}。 */
    public PoleKey opposite(PoleKey key) {
        if (this.master.equals(key)) return this.slave;
        if (this.slave.equals(key)) return this.master;
        return null;
    }
}
