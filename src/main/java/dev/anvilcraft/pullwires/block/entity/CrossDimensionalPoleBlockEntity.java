package dev.anvilcraft.pullwires.block.entity;

import dev.anvilcraft.pullwires.init.AddonBlocks;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerComponentType;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.entity.AbstractTransmissionPoleBlockEntity;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 跨维度输电杆：不供电、不自动组网，只作磁盘连接端点。
 *
 * <p>{@code AbstractTransmissionPoleBlockEntity} 是个空壳（只有构造），
 * {@link IPowerComponent} 有 5 个抽象方法要自己实现；本体靠 Lombok 的
 * {@code @Getter @Setter} 生成 grid 访问器，本模组不引 Lombok，因此手写。
 * {@code flushState} / {@code getShape} / {@code gridTick} 是接口 default，可直接调用。</p>
 */
public class CrossDimensionalPoleBlockEntity extends AbstractTransmissionPoleBlockEntity {
    private @Nullable PowerGrid grid;

    public CrossDimensionalPoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static CrossDimensionalPoleBlockEntity createBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        return new CrossDimensionalPoleBlockEntity(type, pos, state);
    }

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

    /**
     * 中继元件只在<b>顶段</b>成立。
     *
     * <p>必须校验<b>我们自己的</b>方块——本体那份实现校验的是本体的远程杆，
     * 照抄会让我们的杆永远是 INVALID。</p>
     */
    @Override
    public PowerComponentType getComponentType() {
        if (this.getLevel() == null) return PowerComponentType.INVALID;
        if (!this.getBlockState().is(AddonBlocks.CROSS_DIMENSIONAL_POLE.get())) return PowerComponentType.INVALID;
        if (this.getBlockState().getValue(RemoteTransmissionPoleBlock.HALF) != Vertical4PartHalf.TOP) {
            return PowerComponentType.INVALID;
        }
        return PowerComponentType.TRANSMITTER;
    }

    @Override
    public int getRange() {
        return 0;
    }

    /** 由方块的 ticker 调用：跟随 SWITCH 入网 / 退网，然后同步方块状态。 */
    public void tick(Level level, BlockPos pos) {
        // 方块实体的 tick 在客户端也会跑。不挡住的话会在 ClientLevel 上建出一座电网，
        // 本体服务端的 PowerGridManager 遍历到它时会 ClassCastException 崩溃。
        if (level.isClientSide) return;
        BlockState state = this.getBlockState();
        if (state.getValue(RemoteTransmissionPoleBlock.SWITCH) == IPowerComponent.Switch.OFF) {
            if (this.getGrid() != null) this.getGrid().remove(this);
        } else if (this.getGrid() == null) {
            PowerGrid.addComponent(this);
        }
        this.flushState(level, pos);
    }
}