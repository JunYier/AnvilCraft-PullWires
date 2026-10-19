package dev.anvilcraft.pullwires.block.entity;

import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerComponentType;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.entity.AbstractTransmissionPoleBlockEntity;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 本模组两种电线杆的公共方块实体基类。
 *
 * <p>{@code AbstractTransmissionPoleBlockEntity} 是个空壳（只有构造），而 {@link IPowerComponent}
 * 有 5 个抽象方法必须自己实现：本体靠 Lombok 的 {@code @Getter @Setter} 生成 grid 访问器，
 * 本模组不引 Lombok，所以在这里手写一次，两个子类各自只声明「自己是哪根杆」。</p>
 *
 * <p>{@code flushState} / {@code getShape} / {@code gridTick} 是接口 default，可直接调用。</p>
 *
 * <p>进网 / 退网跟随 {@code SWITCH} 状态；同步方块状态由 {@code flushState} 负责。
 * 跨维度杆额外需要区块强加载，走 {@link #onServerTick(Level)} 钩子。</p>
 */
public abstract class AbstractAddonPoleBlockEntity extends AbstractTransmissionPoleBlockEntity {
    private @Nullable PowerGrid grid;

    protected AbstractAddonPoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 本实体对应的方块。
     *
     * <p>{@link #getComponentType()} 用它确认「这是我们自己的杆」——本体那份实现校验的是
     * 本体的远程杆，照抄会让我们的杆永远是 {@code INVALID}。</p>
     */
    protected abstract Block ownBlock();

    /** 本杆的种类：供电范围从配置里按它查。 */
    protected abstract PoleKind kind();

    @Override
    public BlockPos getPos() {
        return this.getBlockPos();
    }

    @Override
    public Level getCurrentLevel() {
        return Objects.requireNonNull(this.getLevel());
    }

    @Override
    public @Nullable PowerGrid getGrid() {
        return this.grid;
    }

    @Override
    public void setGrid(PowerGrid value) {
        this.grid = value;
    }

    /** 中继元件只在<b>顶段</b>成立。 */
    @Override
    public PowerComponentType getComponentType() {
        if (this.getLevel() == null) return PowerComponentType.INVALID;
        if (!this.getBlockState().is(this.ownBlock())) return PowerComponentType.INVALID;
        if (this.getBlockState().getValue(RemoteTransmissionPoleBlock.HALF) != Vertical4PartHalf.TOP) {
            return PowerComponentType.INVALID;
        }
        return PowerComponentType.TRANSMITTER;
    }

    /** 供电范围取自本模组配置，而不是写死。 */
    @Override
    public int getRange() {
        return PoleKind.supplyRange(this.kind());
    }

    /** 由方块的 ticker 调用：跟随 {@code SWITCH} 入网 / 退网，然后同步方块状态。 */
    public void tick(Level level, BlockPos pos) {
        // 方块实体的 tick 在客户端也会跑。不挡住的话会在 ClientLevel 上建出一座电网，
        // 本体服务端的 PowerGridManager 遍历到它时会 ClassCastException 崩溃。
        if (level.isClientSide) return;
        this.onServerTick(level);
        BlockState state = this.getBlockState();
        if (state.getValue(RemoteTransmissionPoleBlock.SWITCH) == IPowerComponent.Switch.OFF) {
            if (this.getGrid() != null) this.getGrid().remove(this);
        } else if (this.getGrid() == null) {
            PowerGrid.addComponent(this);
        }
        this.flushState(level, pos);
    }

    /** 服务端每 tick 的附加逻辑（默认什么都不做）。 */
    protected void onServerTick(Level level) {
    }
}
