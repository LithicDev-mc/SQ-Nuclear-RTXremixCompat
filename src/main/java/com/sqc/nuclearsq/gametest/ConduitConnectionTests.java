package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.transport.ConduitBlock;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.ConduitSide;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ConduitConnectionTests {
    private ConduitConnectionTests() {}

    private static final String FLOOR = "floor";

    private static Block[] kinds() {
        return new Block[] {TransportRegistry.PIPES.get(TransportTier.BASIC).get(), TransportRegistry.ITEM_PIPES.get(TransportTier.BASIC).get(),
                TransportRegistry.CABLES.get(TransportTier.BASIC).get()};
    }

    @GameTest(template = FLOOR, batch = "conduit_conn_state", timeoutTicks = 40)
    public static void connectingKeepsBlockState(GameTestHelper h) {
        Block[] kinds = kinds();
        for (int k = 0; k < kinds.length; k++) {
            BlockPos a = h.absolutePos(new BlockPos(1 + 2 * k, 2, 2)), b = a.south();
            h.getLevel().setBlockAndUpdate(a, kinds[k].defaultBlockState());
            h.getLevel().setBlockAndUpdate(b, kinds[k].defaultBlockState());
            h.getLevel().setBlockAndUpdate(a, kinds[k].defaultBlockState());
            h.getLevel().setBlockAndUpdate(b, kinds[k].defaultBlockState());
        }
        h.runAfterDelay(5, () -> {
            for (int k = 0; k < kinds.length; k++) {
                BlockPos a = h.absolutePos(new BlockPos(1 + 2 * k, 2, 2));
                if (h.getLevel().getBlockState(a) != kinds[k].defaultBlockState()) {
                    throw new GameTestAssertException(kinds[k] + ": Blockstate hat sich durch das Verbinden geaendert: " + h.getLevel().getBlockState(a));
                }
                if (ConduitBlock.sideAt(h.getLevel(), a, Direction.SOUTH) == ConduitSide.NONE) throw new GameTestAssertException(kinds[k] + " nicht verbunden");
                ConduitBlockEntity be = (ConduitBlockEntity) h.getLevel().getBlockEntity(a);
                CompoundTag tag = be.getUpdateTag(h.getLevel().registryAccess());
                if (tag.getInt("Conn") != be.conn() || be.conn() == 0) throw new GameTestAssertException(kinds[k] + ": Update-Tag ohne Verbindungen");
            }
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "conduit_conn_old_world", timeoutTicks = 60)
    public static void oldWorldConduitsReconnect(GameTestHelper h) {
        Block[] kinds = kinds();
        for (int k = 0; k < kinds.length; k++) {
            BlockPos a = h.absolutePos(new BlockPos(1 + 2 * k, 2, 2)), b = a.south();
            h.getLevel().setBlockAndUpdate(a, kinds[k].defaultBlockState());
            h.getLevel().setBlockAndUpdate(b, kinds[k].defaultBlockState());
        }
        h.runAfterDelay(3, () -> {
            for (int k = 0; k < kinds.length; k++) {
                for (BlockPos p : new BlockPos[] {h.absolutePos(new BlockPos(1 + 2 * k, 2, 2)), h.absolutePos(new BlockPos(1 + 2 * k, 2, 3))}) {
                    BlockEntity old = h.getLevel().getBlockEntity(p);
                    CompoundTag tag = old.saveWithFullMetadata(h.getLevel().registryAccess());
                    tag.remove("Conn");
                    BlockEntity fresh = BlockEntity.loadStatic(p, h.getLevel().getBlockState(p), tag, h.getLevel().registryAccess());
                    h.getLevel().setBlockEntity(fresh);
                    if (((ConduitBlockEntity) fresh).conn() != 0) throw new GameTestAssertException("Testaufbau: Verbindungen nicht geleert");
                }
            }
            h.runAfterDelay(5, () -> {
                for (int k = 0; k < kinds.length; k++) {
                    BlockPos a = h.absolutePos(new BlockPos(1 + 2 * k, 2, 2)), b = a.south();
                    if (ConduitBlock.sideAt(h.getLevel(), a, Direction.SOUTH) == ConduitSide.NONE
                            || ConduitBlock.sideAt(h.getLevel(), b, Direction.NORTH) == ConduitSide.NONE) {
                        throw new GameTestAssertException(kinds[k] + " verbindet sich nach dem Laden ohne gespeicherte Verbindungen nicht wieder");
                    }
                }
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "conduit_conn_shape", timeoutTicks = 40)
    public static void shapeFollowsConnections(GameTestHelper h) {
        BlockPos a = h.absolutePos(new BlockPos(2, 2, 2));
        Block pipe = TransportRegistry.PIPES.get(TransportTier.BASIC).get();
        h.getLevel().setBlockAndUpdate(a, pipe.defaultBlockState());
        h.runAfterDelay(3, () -> {
            double lone = h.getLevel().getBlockState(a).getShape(h.getLevel(), a).bounds().getZsize();
            h.getLevel().setBlockAndUpdate(a.south(), pipe.defaultBlockState());
            h.getLevel().setBlockAndUpdate(a.north(), pipe.defaultBlockState());
            h.runAfterDelay(3, () -> {
                double joined = h.getLevel().getBlockState(a).getShape(h.getLevel(), a).bounds().getZsize();
                if (joined <= lone + 0.01 || joined < 0.99) throw new GameTestAssertException("Form waechst nicht mit den Verbindungen: " + lone + " -> " + joined);
                h.succeed();
            });
        });
    }

    @GameTest(template = FLOOR, batch = "conduit_conn_relog", timeoutTicks = 300)
    public static void pipeKeepsMachinePortAfterRelog(GameTestHelper h) {
        BigMachine m = BigMachine.RELIEF_TANK;
        BlockPos ctrl = h.absolutePos(new BlockPos(3, 4, 3)).subtract(m.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        BigMachine.Port port = m.port("relief_in");
        BlockPos portPos = m.structure.world(ctrl, 0, port.x(), port.y(), port.z());
        Direction d = null;
        for (Direction t : Direction.values()) if (h.getLevel().getBlockState(portPos.relative(t)).isAir()) d = t;
        if (d == null) throw new GameTestAssertException("Testaufbau: kein freier Platz am Port");
        BlockPos pipe = portPos.relative(d);
        Direction toPort = d.getOpposite();
        h.getLevel().setBlockAndUpdate(pipe, TransportRegistry.PIPES.get(TransportTier.BASIC).get().defaultBlockState());
        h.runAfterDelay(60, () -> {
            if (ConduitBlock.sideAt(h.getLevel(), pipe, toPort) == ConduitSide.NONE) throw new GameTestAssertException("Testaufbau: Rohr nicht mit dem Port verbunden");
            var reg = h.getLevel().registryAccess();
            CompoundTag ctrlTag = h.getLevel().getBlockEntity(ctrl).saveWithFullMetadata(reg);
            h.getLevel().removeBlockEntity(ctrl);
            for (BlockPos p : new BlockPos[] {pipe, portPos}) {
                CompoundTag tag = h.getLevel().getBlockEntity(p).saveWithFullMetadata(reg);
                h.getLevel().setBlockEntity(BlockEntity.loadStatic(p, h.getLevel().getBlockState(p), tag, reg));
            }
            h.runAfterDelay(5, () -> h.getLevel().setBlockEntity(BlockEntity.loadStatic(ctrl, h.getLevel().getBlockState(ctrl), ctrlTag, reg)));
            h.runAfterDelay(100, () -> {
                if (ConduitBlock.sideAt(h.getLevel(), pipe, toPort) == ConduitSide.NONE) throw new GameTestAssertException("Rohr hat die Verbindung zum Port nach dem Laden verloren");
                h.succeed();
            });
        });
    }
}
