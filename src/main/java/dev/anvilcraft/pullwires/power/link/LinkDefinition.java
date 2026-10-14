package dev.anvilcraft.pullwires.power.link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * 一条链路的<b>定义</b>，会写入存档。
 *
 * <p>只记录「谁连谁、谁是主端、什么时候建的」，不记录任何运行时状态（见 {@link LinkState}）。
 * 这样「触发亏电断路」只是把状态置为 {@link LinkState#SUSPENDED}，
 * 定义仍然保留；开关重启时清空运行时值即可让链路自然恢复，
 * 与「重置所有值，但保留手动连接的值」这条需求完全对应。</p>
 *
 * <h2>有向边（V1.4.1 起）</h2>
 * <p>{@code master → slave} 是<b>有向</b>的：电力只能由主端流向从端。
 * 因此允许 {@code A → B} 与 {@code B → A} 同时存在——它们是两条独立的链路。
 * 唯一禁止的是<b>同向重复</b>与<b>自环</b>。</p>
 *
 * @param id        链路 UUID
 * @param master    主端：管理权归属方，同时是<b>电力发送方</b>
 * @param slave     从端：只接收
 * @param createdAt 建立序号，同一主端下优先级相同时按它升序排序
 */
public record LinkDefinition(UUID id, PoleKey master, PoleKey slave, long createdAt) {
    public static final Codec<LinkDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(LinkDefinition::id),
        PoleKey.CODEC.fieldOf("master").forGetter(LinkDefinition::master),
        PoleKey.CODEC.fieldOf("slave").forGetter(LinkDefinition::slave),
        Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(LinkDefinition::createdAt)
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

    /** 是否与另一条链路同向重叠（同为主端、同为从端时才算重复）。 */
    public boolean sameDirectionAs(LinkDefinition other) {
        return this.master.equals(other.master) && this.slave.equals(other.slave);
    }
}
