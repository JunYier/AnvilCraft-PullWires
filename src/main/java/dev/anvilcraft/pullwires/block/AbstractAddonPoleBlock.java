package dev.anvilcraft.pullwires.block;

import dev.anvilcraft.pullwires.block.entity.AbstractAddonPoleBlockEntity;
import dev.dubhe.anvilcraft.block.RemoteTransmissionPoleBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 本模组两种电线杆的公共方块基类。
 *
 * <p>超远程杆与跨维度杆都复用本体远程输电桩的四段多部分结构，差异只有三处：
 * 方块实体类型、顶段碰撞箱的上半部分、以及跨维度杆独有的区块强加载。
 * 所以「ticker 转发」与「头部下段公共碰撞箱」放在这里共用。</p>
 *
 * <p><b>ticker 必须重写</b>：本体那份会把方块实体强转成本体类型，我们的方块实体不是它的子类，
 * 不重写就会在第一个 tick 抛 {@code ClassCastException}。</p>
 */
public abstract class AbstractAddonPoleBlock extends RemoteTransmissionPoleBlock {
    /**
     * 两种杆头部模型共有的<b>下段</b>碰撞箱：底块 8×8、立柱 6×6、中部圆盘 10×10。
     *
     * <p>两套美术的头部上半部分不同（超远程杆收到 y=13.25，跨维度杆是 8×8×8 外壳到 y=16），
     * 但下段几何完全一致，因此共用这一份；各子类只需再叠上自己的上半部分。</p>
     *
     * <p>碰撞箱必须跟着视觉模型走，否则会出现「看着穿过去了却撞在空气上」。</p>
     */
    protected static final VoxelShape HEAD_LOWER_SHAPE = Shapes.or(
        Block.box(4, 0, 4, 12, 2, 12),
        Block.box(5, 2, 5, 11, 4, 11),
        Block.box(3, 4, 3, 13, 6, 13)
    );

    protected AbstractAddonPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AbstractAddonPoleBlockEntity pole) pole.tick(tickLevel, pos);
        };
    }
}
