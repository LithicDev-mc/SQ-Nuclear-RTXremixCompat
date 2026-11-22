package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.power.RtgBlock;
import com.sqc.nuclearsq.power.RtgBlockEntity;
import com.sqc.nuclearsq.radiation.RadiationManager;
import com.sqc.nuclearsq.registry.PowerRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RtgTests {
    private RtgTests() {}

    private static final String FLOOR = "floor";

    private static RtgBlockEntity rtg(GameTestHelper h, BlockPos rel) {
        if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof RtgBlockEntity r)) throw new GameTestAssertException("Kein RTG bei " + rel);
        return r;
    }

    private static ItemStack capsule() {
        return new ItemStack(PowerRegistry.ISOTOPE_CAPSULE.get());
    }

    @GameTest(template = FLOOR, batch = "rtg", timeoutTicks = 20)
    public static void decayFollowsHalfLife(GameTestHelper h) {
        if (RtgBlockEntity.rateFor(0) != RtgBlockEntity.PEAK) throw new GameTestAssertException("Frisch nicht PEAK: " + RtgBlockEntity.rateFor(0));
        if (RtgBlockEntity.rateFor(RtgBlockEntity.HALF_LIFE_STEPS) != RtgBlockEntity.PEAK / 2) throw new GameTestAssertException("Halbwertszeit nicht die Haelfte");
        if (RtgBlockEntity.rateFor(2 * RtgBlockEntity.HALF_LIFE_STEPS) != RtgBlockEntity.PEAK / 4) throw new GameTestAssertException("Zwei Halbwertszeiten nicht ein Viertel");
        int last = RtgBlockEntity.rateFor(RtgBlockEntity.CAPSULE_STEPS - 1);
        if (last <= 0 || last > RtgBlockEntity.PEAK / 16 + 1) throw new GameTestAssertException("Am Ende noch " + last + " FE/t statt rund " + RtgBlockEntity.PEAK / 16);
        if (RtgBlockEntity.rateFor(RtgBlockEntity.CAPSULE_STEPS) != 0) throw new GameTestAssertException("Leere Kapsel gibt Strom");
        if (RtgBlockEntity.PEAK <= 160 || RtgBlockEntity.PEAK >= RtgBlockEntity.PEAK_PU238) throw new GameTestAssertException("PEAK " + RtgBlockEntity.PEAK + " ausserhalb 160..PEAK_PU238");
        if (new ItemStack(PowerRegistry.ISOTOPE_CAPSULE.get()).getMaxDamage() != RtgBlockEntity.CAPSULE_STEPS) throw new GameTestAssertException("Kapsel-Haltbarkeit stimmt nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "rtg", timeoutTicks = 200)
    public static void feedsBatteryDecaysAndGivesCapsuleBack(GameTestHelper h) {
        BlockPos sink = new BlockPos(2, 2, 3), at = new BlockPos(3, 2, 3);
        BigMachineBlockEntity bat = TestSinks.battery(h, sink, Direction.EAST);
        RtgBlock.place(h.getLevel(), h.absolutePos(at));
        RtgBlockEntity r = rtg(h, at);
        if (r.hasCapsule() || h.getBlockState(at).getValue(RtgBlock.LOADED)) throw new GameTestAssertException("Neuer RTG ist nicht leer");
        for (BlockPos part : new BlockPos[] {at, at.above()}) {
            for (Direction d : Direction.values()) {
                IEnergyStorage s = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(part), d);
                if (s == null || s.canReceive() || s.getEnergyStored() != 0) throw new GameTestAssertException("Anschluss " + d + " bei " + part + " fehlt oder hat Speicher");
            }
        }
        if (!h.getBlockState(at.above()).is(PowerRegistry.RTG.get()) || h.getBlockState(at.above()).getValue(RtgBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
            throw new GameTestAssertException("Oberer Teil fehlt");
        if (!r.insert(capsule())) throw new GameTestAssertException("Kapsel passt nicht in den leeren RTG");
        if (r.insert(capsule())) throw new GameTestAssertException("Zweite Kapsel passt in den vollen RTG");
        if (!h.getBlockState(at).getValue(RtgBlock.LOADED) || !h.getBlockState(at.above()).getValue(RtgBlock.LOADED))
            throw new GameTestAssertException("Block zeigt die Kapsel nicht (loaded, beide Teile)");
        h.runAfterDelay(50, () -> {
            if (r.rate() != RtgBlockEntity.PEAK) throw new GameTestAssertException("Leistung " + r.rate() + " statt " + RtgBlockEntity.PEAK);
            if (r.delivered() != RtgBlockEntity.PEAK) throw new GameTestAssertException("Abgegeben " + r.delivered());
            if (bat.energy().getEnergyStored() < RtgBlockEntity.PEAK * 10) throw new GameTestAssertException("Akku bekam nur " + bat.energy().getEnergyStored());
            if (RadiationManager.emitted(h.getLevel(), h.absolutePos(at)) <= 0) throw new GameTestAssertException("RTG strahlt nicht");
            r.capsule().setDamageValue(RtgBlockEntity.HALF_LIFE_STEPS);
            h.runAfterDelay(30, () -> {
                if (r.rate() != RtgBlockEntity.PEAK / 2 && r.rate() != RtgBlockEntity.rateFor(RtgBlockEntity.HALF_LIFE_STEPS + 1)) throw new GameTestAssertException("Nach einer Halbwertszeit " + r.rate() + " FE/t");
                if (r.remainingPercent() != 75) throw new GameTestAssertException("Rest " + r.remainingPercent() + " % statt 75");
                ItemStack back = r.take();
                if (!back.is(PowerRegistry.ISOTOPE_CAPSULE.get()) || back.getDamageValue() < RtgBlockEntity.HALF_LIFE_STEPS)
                    throw new GameTestAssertException("Kapsel ohne ihren Zerfallsstand zurueck: " + back);
                if (r.hasCapsule() || h.getBlockState(at).getValue(RtgBlock.LOADED)) throw new GameTestAssertException("RTG nach dem Entnehmen nicht leer");
                h.runAfterDelay(20, () -> {
                    if (r.rate() != 0 || r.delivered() != 0) throw new GameTestAssertException("Leerer RTG gibt noch Strom");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "rtg", timeoutTicks = 100)
    public static void spentCapsuleGivesNothing(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 2, 3);
        RtgBlock.place(h.getLevel(), h.absolutePos(at));
        RtgBlockEntity r = rtg(h, at);
        ItemStack spent = capsule();
        spent.setDamageValue(RtgBlockEntity.CAPSULE_STEPS - 1);
        r.insert(spent);
        h.runAfterDelay(RtgBlockEntity.STEP_TICKS + 10, () -> {
            if (r.rate() != 0 && r.capsule().getDamageValue() < RtgBlockEntity.CAPSULE_STEPS) throw new GameTestAssertException("Kapsel nicht ganz zerfallen");
            if (h.getBlockState(at).getValue(RtgBlock.LOADED)) throw new GameTestAssertException("Leere Kapsel leuchtet noch");
            if (!r.hasCapsule()) throw new GameTestAssertException("Leere Kapsel verschwunden");
            if (r.remainingPercent() != 0) throw new GameTestAssertException("Rest " + r.remainingPercent() + " %");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "rtg", timeoutTicks = 60)
    public static void rightClickInsertsAndSneakTakesOut(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 2, 3);
        RtgBlock.place(h.getLevel(), h.absolutePos(at));
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.setGameMode(GameType.SURVIVAL);
        sp.getInventory().clearContent();
        ItemStack held = capsule();
        sp.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos abs = h.absolutePos(at);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        sp.gameMode.useItemOn(sp, h.getLevel(), held, InteractionHand.MAIN_HAND, hit);
        if (!rtg(h, at).hasCapsule()) throw new GameTestAssertException("Rechtsklick hat keine Kapsel eingesetzt");
        if (!sp.getMainHandItem().isEmpty()) throw new GameTestAssertException("Kapsel blieb in der Hand");
        sp.setShiftKeyDown(true);
        sp.gameMode.useItemOn(sp, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        if (rtg(h, at).hasCapsule()) throw new GameTestAssertException("Schleichen hat die Kapsel nicht herausgenommen");
        if (sp.getInventory().countItem(PowerRegistry.ISOTOPE_CAPSULE.get()) != 1) throw new GameTestAssertException("Kapsel nicht im Inventar");
        rtg(h, at).insert(capsule());
        h.getLevel().destroyBlock(abs, true);
        h.runAfterDelay(2, () -> {
            var box = new net.minecraft.world.phys.AABB(abs).inflate(3);
            int n = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                    .filter(e -> e.getItem().is(PowerRegistry.ISOTOPE_CAPSULE.get())).mapToInt(e -> e.getItem().getCount()).sum();
            if (n != 1) throw new GameTestAssertException("Beim Abbauen fielen " + n + " Kapseln statt 1 heraus");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "rtg", timeoutTicks = 60)
    public static void breakingTheTopRemovesBothParts(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 2, 3);
        RtgBlock.place(h.getLevel(), h.absolutePos(at));
        rtg(h, at).insert(capsule());
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        sp.setGameMode(GameType.SURVIVAL);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        sp.gameMode.destroyBlock(h.absolutePos(at.above()));
        h.runAfterDelay(3, () -> {
            if (!h.getBlockState(at).isAir() || !h.getBlockState(at.above()).isAir()) throw new GameTestAssertException("Generator nicht ganz abgebaut");
            var box = new net.minecraft.world.phys.AABB(h.absolutePos(at)).inflate(3);
            var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box);
            int gens = drops.stream().filter(e -> e.getItem().is(PowerRegistry.RTG_ITEM.get())).mapToInt(e -> e.getItem().getCount()).sum();
            int caps = drops.stream().filter(e -> e.getItem().is(PowerRegistry.ISOTOPE_CAPSULE.get())).mapToInt(e -> e.getItem().getCount()).sum();
            if (gens != 1 || caps != 1) throw new GameTestAssertException("Drops: " + gens + " Generatoren, " + caps + " Kapseln");
            h.succeed();
        });
    }
}
