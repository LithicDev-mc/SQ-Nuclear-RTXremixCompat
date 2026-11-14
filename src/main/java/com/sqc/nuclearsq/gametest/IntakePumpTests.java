package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.transport.BigPipeBlock;
import com.sqc.nuclearsq.transport.BigPipeBlockEntity;
import com.sqc.nuclearsq.transport.TransportRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class IntakePumpTests {
    private IntakePumpTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine PUMP = BigMachine.INTAKE_PUMP;

    private static BlockPos cell(BlockPos ctrl, int x, int y, int z) {
        return PUMP.structure.world(ctrl, 0, x, y, z);
    }

    private static BigMachineBlockEntity build(GameTestHelper h, boolean wet) {
        BlockPos ctrl = h.absolutePos(new BlockPos(3, 6, 3)).subtract(PUMP.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        if (wet) {
            for (int x : new int[] {2, 3, 5, 6}) for (int z : new int[] {1, 2}) {
                h.getLevel().setBlockAndUpdate(cell(ctrl, x, 0, z), Blocks.WATER.defaultBlockState());
            }
        }
        for (MultiblockStructure.Cell c : PUMP.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
        be.energy().setStored(PUMP.energy.capacity());
        return be;
    }

    @GameTest(template = FLOOR, batch = "intake_dry", timeoutTicks = 120)
    public static void intakePumpNeedsWater(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, false);
        h.runAfterDelay(60, () -> {
            if (!be.isFormed()) h.fail("nicht aufgebaut");
            if (be.tank("water_out_l_1").getFluidAmount() != 0) h.fail("pumpt ohne Wasser");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "intake_wet", timeoutTicks = 200)
    public static void intakePumpFillsPortsAndKeepsLake(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, true);
        h.succeedWhen(() -> {
            if (!be.isFormed()) throw new GameTestAssertException("nicht aufgebaut");
            for (String side : new String[] {"l", "r"}) for (int k = 1; k <= 4; k++) {
                int a = be.tank("water_out_" + side + "_" + k).getFluidAmount();
                if (a <= 0) throw new GameTestAssertException("water_out_" + side + "_" + k + " leer");
            }
            for (int k = 1; k <= 2; k++) if (be.tank("water_out_s_" + k).getFluidAmount() <= 0) throw new GameTestAssertException("water_out_s_" + k + " leer");
            for (int x : new int[] {2, 3, 5, 6}) for (int z : new int[] {1, 2}) {
                if (!h.getLevel().getFluidState(cell(be.getBlockPos(), x, 0, z)).isSource()) throw new GameTestAssertException("See-Wasser verbraucht");
            }
        });
    }

    @GameTest(template = FLOOR, batch = "intake_pipe", timeoutTicks = 300)
    public static void bigPipePlacesAndCarriesWater(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, true);
        h.runAfterDelay(45, () -> bigPipeCheck(h, be));
    }

    private static void bigPipeCheck(GameTestHelper h, BigMachineBlockEntity be) {
        if (!be.isFormed()) throw new GameTestAssertException("nicht aufgebaut");
        BlockPos ctrl = be.getBlockPos();
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        BlockPos port = cell(ctrl, 1, 6, 8);
        ItemStack stack = new ItemStack(TransportRegistry.BIG_PIPE_ITEM.get(), 2);
        var res = stack.getItem() instanceof com.sqc.nuclearsq.transport.BigPipeItem it
                ? it.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(port), Direction.SOUTH, port, false)))
                : InteractionResult.FAIL;
        if (!res.consumesAction()) {
            BlockPos t = port.south();
            throw new GameTestAssertException("Platzieren am Anschluss klappt nicht: " + res + ", Port=" + h.getLevel().getBlockState(port) + ", formed=" + be.isFormed()
                    + ", Ziel=" + h.getLevel().getBlockState(t) + ", mayInteract=" + h.getLevel().mayInteract(player, t) + ", y=" + t.getY() + ", item=" + stack.getItem() + ", cells=" + java.util.stream.IntStream.range(0, 4).mapToObj(q -> {
                BlockPos c = BigPipeBlock.cell(cell(ctrl, 0, 5, 9), Direction.Axis.Z, q);
                return c.toShortString() + ":" + h.getLevel().getBlockState(c).getBlock() + ":" + h.getLevel().getBlockState(c).canBeReplaced() + ":" + h.getLevel().isOutsideBuildHeight(c);
            }).toList());
        }
        BlockPos o = cell(ctrl, 0, 5, 9);
        for (int q = 0; q < 4; q++) {
            var st = h.getLevel().getBlockState(BigPipeBlock.cell(o, Direction.Axis.Z, q));
            if (!st.is(TransportRegistry.BIG_PIPE.get()) || st.getValue(BigPipeBlock.QUAD) != q) throw new GameTestAssertException("Viertel " + q + " fehlt");
        }
        BlockPos front = BigPipeBlock.cell(o, Direction.Axis.Z, 3);
        var res2 = ((com.sqc.nuclearsq.transport.BigPipeItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(front), Direction.SOUTH, front, false)));
        if (!res2.consumesAction()) throw new GameTestAssertException("zweites Stueck klappt nicht");
        BlockPos o2 = o.relative(Direction.SOUTH);
        h.succeedWhen(() -> {
            for (int q = 0; q < 4; q++) {
                if (!(h.getLevel().getBlockEntity(BigPipeBlock.cell(o2, Direction.Axis.Z, q)) instanceof BigPipeBlockEntity p) || p.tank().getFluidAmount() <= 0) {
                    throw new GameTestAssertException("kein Wasser im zweiten Stueck, Viertel " + q);
                }
            }
            h.getLevel().destroyBlock(BigPipeBlock.cell(o2, Direction.Axis.Z, 2), false);
            for (int q = 0; q < 4; q++) {
                if (h.getLevel().getBlockState(BigPipeBlock.cell(o2, Direction.Axis.Z, q)).is(TransportRegistry.BIG_PIPE.get())) throw new GameTestAssertException("Viertel " + q + " steht noch");
            }
        });
    }
}
