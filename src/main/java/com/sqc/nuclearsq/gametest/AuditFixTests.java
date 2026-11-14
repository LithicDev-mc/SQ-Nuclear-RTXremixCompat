package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import com.sqc.nuclearsq.transport.GridNode;
import com.sqc.nuclearsq.transport.GridNodeBlockEntity;
import com.sqc.nuclearsq.transport.GridRegistry;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;
import com.sqc.nuclearsq.turbine.TurbinePortType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class AuditFixTests {
    private AuditFixTests() {}

    private static final String FLOOR = "floor";

    private static BlockPos ctrlOf(GameTestHelper h, MultiblockStructure s, BlockPos min) {
        return h.absolutePos(min).subtract(s.world(BlockPos.ZERO, 0, 0, 0, 0));
    }

    private static final GameProfile TEST_PROFILE = new GameProfile(UUID.fromString("6e5f0a1c-2b3d-4c5e-8f70-9a1b2c3d4e5f"), "nsq_test");

    static ServerPlayer testPlayer(GameTestHelper h) {
        ServerPlayer sp = FakePlayerFactory.get(h.getLevel(), TEST_PROFILE);
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 3, 1)));
        sp.setPos(at.x, at.y, at.z);
        sp.setShiftKeyDown(false);
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return sp;
    }

    private static ServerPlayer player(GameTestHelper h, ItemStack held, boolean sneak) {
        ServerPlayer sp = testPlayer(h);
        sp.setItemInHand(InteractionHand.MAIN_HAND, held);
        sp.setShiftKeyDown(sneak);
        return sp;
    }

    private static void click(GameTestHelper h, ServerPlayer sp, BlockPos abs) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        sp.gameMode.useItemOn(sp, h.getLevel(), sp.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    @GameTest(template = FLOOR, batch = "audit_rack_index", timeoutTicks = 20)
    public static void rackIgnoresBadShaftIndex(GameTestHelper h) {
        BigMachine m = BigMachine.SPENT_FUEL_RACK;
        BlockPos ctrl = ctrlOf(h, m.structure, new BlockPos(2, 2, 2));
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("kein Controller");
        for (int v : new int[] {-1, 9, 100, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            if (be.rackReleased(v)) throw new GameTestAssertException("Schacht " + v + " gilt als freigegeben");
            be.setRackReleased(v, !be.rackReleased(v));
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "audit_wire", timeoutTicks = 20)
    public static void wireRefundsOnlyOnce(GameTestHelper h) {
        BlockPos pa = new BlockPos(2, 2, 2), pb = new BlockPos(12, 2, 2);
        h.setBlock(pa, GridRegistry.MAST.get().defaultBlockState());
        h.setBlock(pb, GridRegistry.MAST.get().defaultBlockState());
        GridNodeBlockEntity a = h.getBlockEntity(pa), b = h.getBlockEntity(pb);
        GridNode.link(a, b);
        int cost = GridNode.wireCost(h.absolutePos(pa), h.absolutePos(pb));
        int first = a.unlinkAll();
        if (first != cost) throw new GameTestAssertException("erster Mast gibt " + first + " statt " + cost + " Seile zurueck");
        int second = b.unlinkAll();
        if (second != 0) throw new GameTestAssertException("zweiter Mast gibt noch einmal " + second + " Seile zurueck");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "audit_wire", timeoutTicks = 20)
    public static void deadWireLinkGivesNothing(GameTestHelper h) {
        BlockPos pa = new BlockPos(2, 2, 6), pb = new BlockPos(12, 2, 6);
        h.setBlock(pa, GridRegistry.MAST.get().defaultBlockState());
        h.setBlock(pb, GridRegistry.MAST.get().defaultBlockState());
        GridNodeBlockEntity a = h.getBlockEntity(pa), b = h.getBlockEntity(pb);
        GridNode.link(a, b);
        h.getLevel().removeBlockEntity(h.absolutePos(pb));
        h.getLevel().setBlock(h.absolutePos(pb), Blocks.AIR.defaultBlockState(), 2);
        if (!a.links().contains(h.absolutePos(pb))) throw new GameTestAssertException("Testaufbau: Link schon weg");
        int back = a.unlinkAll();
        if (back != 0) throw new GameTestAssertException("toter Link gibt " + back + " Seile zurueck");
        if (!a.links().isEmpty()) throw new GameTestAssertException("toter Link bleibt stehen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "audit_wrench", timeoutTicks = 20)
    public static void wrenchSneakBreaksTransportBlocks(GameTestHelper h) {
        List<BlockPos> spots = List.of(new BlockPos(2, 2, 2), new BlockPos(4, 2, 2));
        h.setBlock(spots.get(0), TransportRegistry.PIPES.get(TransportTier.BASIC).get());
        h.setBlock(spots.get(1), TransportRegistry.CABLES.get(TransportTier.BASIC).get());
        ServerPlayer sp = player(h, new ItemStack(TransportRegistry.WRENCH.get()), true);
        for (BlockPos p : spots) {
            click(h, sp, h.absolutePos(p));
            if (!h.getBlockState(p).isAir()) throw new GameTestAssertException(h.getBlockState(p).getBlock() + " bleibt stehen");
        }
        h.setBlock(spots.get(0), Blocks.STONE);
        if (new ItemStack(TransportRegistry.WRENCH.get()).doesSneakBypassUse(h.getLevel(), h.absolutePos(spots.get(0)), sp)) {
            throw new GameTestAssertException("Schraubenschluessel umgeht Shift auch an Stein");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "audit_port_caps", timeoutTicks = 200)
    public static void bigPortCapabilityFollowsFormation(GameTestHelper h) {
        BigMachine m = BigMachine.RELIEF_TANK;
        BlockPos min = new BlockPos(3, 2, 3);
        BlockPos ctrl = ctrlOf(h, m.structure, min);
        BigMachine.Port in = m.port("relief_in");
        BlockPos port = h.absolutePos(min.offset(in.x(), in.y(), in.z()));
        MultiblockStructure.Cell skip = null;
        for (MultiblockStructure.Cell c : m.structure.cells(ctrl, 0)) {
            if (skip == null && !c.pos().equals(ctrl) && !c.pos().equals(port) && !(c.block() instanceof com.sqc.nuclearsq.machine.big.BigPortBlock)) {
                skip = c;
                continue;
            }
            h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        }
        MultiblockStructure.Cell missing = skip;
        var cache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, h.getLevel(), port, Direction.NORTH);
        h.runAfterDelay(25, () -> {
            if (cache.getCapability() != null) throw new GameTestAssertException("unfertige Anlage: Port hat schon eine Capability");
            h.getLevel().setBlockAndUpdate(missing.pos(), missing.block().defaultBlockState());
            h.runAfterDelay(45, () -> {
                if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be) || !be.isFormed()) {
                    throw new GameTestAssertException("Anlage wird nicht fertig");
                }
                if (cache.getCapability() == null) throw new GameTestAssertException("fertige Anlage: Zwischenspeicher am Port bleibt leer");
                h.getLevel().setBlockAndUpdate(missing.pos(), Blocks.AIR.defaultBlockState());
                h.runAfterDelay(25, () -> {
                    if (cache.getCapability() != null) throw new GameTestAssertException("abgebaute Anlage: Zwischenspeicher am Port bleibt gefuellt");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "audit_turbine_caps", timeoutTicks = 200)
    public static void turbinePortCapabilityFollowsFormation(GameTestHelper h) {
        MultiblockStructure t = MultiblockStructure.TURBINE;
        BlockPos ctrl = ctrlOf(h, t, new BlockPos(4, 2, 0));
        TurbinePortType e = TurbinePortType.ENERGY;
        BlockPos port = t.world(ctrl, 0, e.x, e.y, e.z);
        List<BlockPos> placed = new ArrayList<>();
        MultiblockStructure.Cell skip = null;
        for (MultiblockStructure.Cell c : t.cells(ctrl, 0)) {
            if (skip == null && !c.pos().equals(ctrl) && !c.pos().equals(port) && c.pos().getY() > ctrl.getY()) {
                boolean isPort = false;
                for (TurbinePortType pt : TurbinePortType.values()) isPort |= c.pos().equals(t.world(ctrl, 0, pt.x, pt.y, pt.z));
                if (!isPort) {
                    skip = c;
                    continue;
                }
            }
            h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
            placed.add(c.pos());
        }
        MultiblockStructure.Cell missing = skip;
        placed.add(missing.pos());
        var cache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, h.getLevel(), port, Direction.NORTH);
        h.runAfterDelay(25, () -> {
            if (cache.getCapability() != null) {
                clear(h, placed);
                throw new GameTestAssertException("unfertige Turbine: Energie-Port hat schon eine Capability");
            }
            h.getLevel().setBlockAndUpdate(missing.pos(), missing.block().defaultBlockState());
            h.runAfterDelay(45, () -> {
                IEnergyStorage now = cache.getCapability();
                clear(h, placed);
                if (now == null) throw new GameTestAssertException("fertige Turbine: Zwischenspeicher am Energie-Port bleibt leer");
                h.succeed();
            });
        });
    }

    private static void clear(GameTestHelper h, List<BlockPos> placed) {
        for (int i = placed.size() - 1; i >= 0; i--) h.getLevel().setBlock(placed.get(i), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    private static void buildRod(GameTestHelper h, BlockPos head) {
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) {
            h.setBlock(head.below(i), ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true)
                    .setValue(ReactorProps.GLOWING, true));
        }
        h.setBlock(head, ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true)
                .setValue(ReactorProps.GLOWING, true));
    }

    private static void assertLoose(GameTestHelper h, BlockPos p, String what) {
        BlockState st = h.getBlockState(p);
        if (st.hasProperty(ReactorProps.ROD_FORMED) && st.getValue(ReactorProps.ROD_FORMED)) {
            throw new GameTestAssertException(what + " bleibt zusammengebaut (unsichtbar)");
        }
    }

    @GameTest(template = FLOOR, batch = "audit_rod", timeoutTicks = 20)
    public static void brokenRodPartUnformsRest(GameTestHelper h) {
        BlockPos headA = new BlockPos(2, 6, 2), headB = new BlockPos(5, 6, 2);
        buildRod(h, headA);
        buildRod(h, headB);
        h.destroyBlock(headA);
        for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) assertLoose(h, headA.below(i), "Stabblock " + i + " unter abgebautem Docker");
        h.destroyBlock(headB.below(2));
        assertLoose(h, headB, "Docker ueber abgebautem Stabblock");
        for (int i : new int[] {1, 3, 4}) assertLoose(h, headB.below(i), "Stabblock " + i);
        h.succeed();
    }
}
