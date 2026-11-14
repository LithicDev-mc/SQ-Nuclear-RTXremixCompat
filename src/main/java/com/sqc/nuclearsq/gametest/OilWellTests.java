package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.oil.OilClass;
import com.sqc.nuclearsq.oil.OilRegistry;
import com.sqc.nuclearsq.oil.OilWellBlock;
import com.sqc.nuclearsq.oil.OilWellFeature;
import com.sqc.nuclearsq.oil.OilWellBlockEntity;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class OilWellTests {
    private OilWellTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "oil_well", timeoutTicks = 20)
    public static void classesAndShares(GameTestHelper h) {
        int[] base = {2_000_000, 6_000_000, 12_000_000, 24_000_000};
        for (OilClass c : OilClass.values()) if (c.base != base[c.ordinal()]) throw new GameTestAssertException(c + ": Menge " + c.base);
        int[] n = new int[4];
        RandomSource r = RandomSource.create(42);
        for (int i = 0; i < 20000; i++) n[OilClass.roll(r).ordinal()]++;
        double[] want = {0.45, 0.30, 0.18, 0.07};
        for (int i = 0; i < 4; i++)
            if (Math.abs(n[i] / 20000.0 - want[i]) > 0.02) throw new GameTestAssertException("Anteil Klasse " + i + ": " + n[i] / 20000.0);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "oil_well", timeoutTicks = 20)
    public static void drainAndRefill(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(2, 3, 2));
        h.getLevel().setBlockAndUpdate(pos, OilRegistry.WELL.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(pos) instanceof OilWellBlockEntity be)) throw new GameTestAssertException("Kern ohne BlockEntity");
        long now = h.getLevel().getGameTime();
        be.init(OilClass.MEDIUM, 6_000_000, 6_000_000, now);
        if (be.drain(h.getLevel(), 1_000_000, true) != 1_000_000 || be.amount(h.getLevel()) != 6_000_000) throw new GameTestAssertException("Simulieren veraendert den Vorrat");
        if (be.drain(h.getLevel(), 1_000_000, false) != 1_000_000) throw new GameTestAssertException("Abpumpen");
        if (be.amount(h.getLevel()) > 5_000_000 || be.amount(h.getLevel()) < 4_999_000) throw new GameTestAssertException("Vorrat " + be.amount(h.getLevel()));
        be.init(OilClass.MEDIUM, 6_000_000, 0, now - OilWellBlockEntity.DAY_TICKS);
        int a = be.amount(h.getLevel());
        if (Math.abs(a - 60_000) > 200) throw new GameTestAssertException("Ein Tag Nachfuellen: " + a + " statt 60000");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "oil_well", timeoutTicks = 20)
    public static void handPlacedWellIsSmallAndFull(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(3, 3, 3));
        h.getLevel().setBlockAndUpdate(pos, OilRegistry.WELL.get().defaultBlockState());
        var be = (OilWellBlockEntity) h.getLevel().getBlockEntity(pos);
        if (be.oilClass(h.getLevel()) != OilClass.SMALL || be.amount(h.getLevel()) != 2_000_000) throw new GameTestAssertException("Handquelle nicht klein und voll");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "oil_well", timeoutTicks = 20)
    public static void stainsLeaveNoBlockEntityStub(GameTestHelper h) {
        var level = h.getLevel();
        ChunkPos cp = new ChunkPos(100_000, 100_000);
        ProtoChunk proto = new ProtoChunk(cp, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        BlockState core = OilRegistry.WELL.get().defaultBlockState();
        BlockState stain = core.setValue(OilWellBlock.CORE, false);
        BlockPos corePos = cp.getBlockAt(8, 64, 8);
        BlockPos[] stains = {cp.getBlockAt(7, 64, 8), cp.getBlockAt(9, 64, 8), cp.getBlockAt(8, 63, 9), cp.getBlockAt(8, 64, 7)};
        for (BlockPos p : stains) {
            placeLikeWorldGen(proto, p, stain);
            OilWellFeature.dropStainEntity(proto, p);
        }
        placeLikeWorldGen(proto, corePos, core);
        OilWellFeature.dropStainEntity(proto, corePos);
        for (BlockPos p : stains)
            if (proto.getBlockEntityNbt(p) != null) throw new GameTestAssertException("Fleck " + p + " hat noch einen Platzhalter");
        if (proto.getBlockEntityNbt(corePos) == null) throw new GameTestAssertException("Kern hat keinen Platzhalter mehr");
        LevelChunk full = new LevelChunk(level, proto, null);
        full.postProcessGeneration();
        if (!full.getBlockEntities().keySet().equals(Set.of(corePos)))
            throw new GameTestAssertException("BlockEntities nach dem Umwandeln: " + full.getBlockEntities().keySet());
        if (!(full.getBlockEntity(corePos) instanceof OilWellBlockEntity)) throw new GameTestAssertException("Kern ohne OilWellBlockEntity");
        for (BlockPos p : stains) if (full.getBlockState(p) != stain) throw new GameTestAssertException("Fleck " + p + " fehlt");
        h.succeed();
    }

    private static void placeLikeWorldGen(ProtoChunk chunk, BlockPos p, BlockState state) {
        chunk.setBlockState(p, state, false);
        if (state.hasBlockEntity()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", p.getX());
            tag.putInt("y", p.getY());
            tag.putInt("z", p.getZ());
            tag.putString("id", "DUMMY");
            chunk.setBlockEntityNbt(tag);
        }
    }
}
