package dev.anvilcraft.pullwires.block.entity;

import dev.anvilcraft.pullwires.AnvilCraftPullWires;
import dev.anvilcraft.pullwires.init.AddonBlocks;
import dev.anvilcraft.pullwires.power.link.PoleKind;
import dev.dubhe.anvilcraft.api.power.IPowerComponent;
import dev.dubhe.anvilcraft.api.power.PowerComponentType;
import dev.dubhe.anvilcraft.api.power.PowerGrid;
import dev.dubhe.anvilcraft.api.world.load.LevelLoadManager;
import dev.dubhe.anvilcraft.api.world.load.LoadChuckData;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import dev.dubhe.anvilcraft.block.entity.AbstractTransmissionPoleBlockEntity;
import dev.dubhe.anvilcraft.block.state.Vertical4PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 *
 * <h2>自己那一格常加载</h2>
 * <p>跨维度链路的对端在别的维度，没人过去时那一侧会卸载，链路就只能停在 {@code INACTIVE}。
 * 这里直接调用本体「监督者」用的那套 {@link LevelLoadManager}，把<b>自己所在的这一个区块</b>
 * 强加载（{@code level = 0} → 边长为 1 的加载区），不申请随机刻。</p>
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

    /** 不供电：只做链路端点，供电范围恒为 0（配置里也固定为 0）。 */
    @Override
    public int getRange() {
        return PoleKind.supplyRange(PoleKind.CROSS_DIMENSIONAL);
    }

    /** 由方块的 ticker 调用：跟随 SWITCH 入网 / 退网，然后同步方块状态。 */
    public void tick(Level level, BlockPos pos) {
        // 方块实体的 tick 在客户端也会跑。不挡住的话会在 ClientLevel 上建出一座电网，
        // 本体服务端的 PowerGridManager 遍历到它时会 ClassCastException 崩溃。
        if (level.isClientSide) return;
        this.keepOwnChunkLoaded(level);
        BlockState state = this.getBlockState();
        if (state.getValue(RemoteTransmissionPoleBlock.SWITCH) == IPowerComponent.Switch.OFF) {
            if (this.getGrid() != null) this.getGrid().remove(this);
        } else if (this.getGrid() == null) {
            PowerGrid.addComponent(this);
        }
        this.flushState(level, pos);
    }

    /**
     * 把自己的单区块登记为强加载；已经登记过就什么都不做。
     *
     * <p>幂等是必要的：{@link LevelLoadManager} 的登记表是<b>内存态</b>，服务器重启后是空的，
     * 而 {@code setChunkForced} 写的强制加载标志会留在存档里。靠每 tick 一次的
     * {@code checkRegistered} 兜底，重启后第一次 tick 就会重新对上，不会出现「标志还在、没人负责」。</p>
     *
     * <p>只有顶段登记：杆是四段多部分方块，四段各登记一次会让同一区块被计 4 份引用。</p>
     */
    private void keepOwnChunkLoaded(Level level) {
        if (!AnvilCraftPullWires.CONFIG.transmission.crossDimensionalPoleForceload) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (this.getComponentType() != PowerComponentType.TRANSMITTER) return;
        BlockPos pos = this.getBlockPos();
        if (LevelLoadManager.checkRegistered(pos, serverLevel)) return;
        // level = 0：加载区边长 2*0+1 = 1 个区块；不要随机刻。
        LevelLoadManager.register(pos, LoadChuckData.createLoadChuckData(0, pos, false, serverLevel), serverLevel);
    }

    /**
     * 解除强加载。
     *
     * <p><b>刻意不放在 {@code setRemoved()} 里。</b>那个回调在<b>区块卸载与服务器关停</b>时也会跑，
     * 而关停路径上做两件事都是致命的：</p>
     * <ul>
     *   <li>读 {@code level.getBlockState(...)}：会去访问一个正在卸载的区块，可能把存档流程卡住；</li>
     *   <li>{@code setChunkForced(false)}：在「Saving worlds」阶段改强制加载集合，
     *       实测表现为<b>日志停在 “Saving worlds” 之后再无输出，世界存不下去</b>。</li>
     * </ul>
     * <p>所以改成由拆杆事件（{@code ManualLinkEvents#onBreak}）直接调用本方法：
     * 那是服务端线程上的明确「方块没了」事件，不存在卸载时序问题。
     * 代价是 {@code /setblock}、爆炸等绕过该事件的方式不会解除强加载——
     * 与本体「监督者」的行为一致。</p>
     */
    public void releaseChunkLoad() {
        if (this.level instanceof ServerLevel serverLevel) {
            LevelLoadManager.unregister(this.worldPosition, serverLevel);
        }
    }
}