package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.radiation.ProtectionRegistry;
import com.sqc.nuclearsq.radiation.RadiationValues;
import com.sqc.nuclearsq.radiation.RadiationWorldData;
import com.sqc.nuclearsq.radiation.Shielding;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class FalloutShieldTests {
    private FalloutShieldTests() {}

    private static final String FLOOR = "floor";

    @GameTest(template = FLOOR, batch = "fallout_support", timeoutTicks = 20)
    public static void falloutFallsWithGround(GameTestHelper h) {
        BlockPos ground = new BlockPos(1, 3, 1);
        BlockPos top = ground.above();
        h.setBlock(top.above(), Blocks.AIR);
        h.setBlock(ground, Blocks.STONE);
        h.setBlock(top, CatalogRegistry.FALLOUT.get());
        long abs = h.absolutePos(top).asLong();
        if (!RadiationWorldData.get(h.getLevel()).hasFallout(abs)) throw new GameTestAssertException("Niederschlag nicht eingetragen");
        h.setBlock(ground, Blocks.AIR);
        h.assertBlockPresent(Blocks.AIR, top);
        if (RadiationWorldData.get(h.getLevel()).hasFallout(abs)) throw new GameTestAssertException("Niederschlag nach Wegfallen noch eingetragen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "fallout_support", timeoutTicks = 20)
    public static void falloutNeedsSolidGround(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 4, 2);
        h.setBlock(p.below(), Blocks.AIR);
        h.setBlock(p, Blocks.AIR);
        BlockState st = CatalogRegistry.FALLOUT.get().defaultBlockState();
        if (st.canSurvive(h.getLevel(), h.absolutePos(p))) throw new GameTestAssertException("Niederschlag haelt sich in der Luft");
        h.setBlock(p.below(), Blocks.STONE);
        if (!st.canSurvive(h.getLevel(), h.absolutePos(p))) throw new GameTestAssertException("Niederschlag haelt sich nicht auf Stein");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "shield_tags", timeoutTicks = 20)
    public static void shieldingFromTags(GameTestHelper h) {
        expect("Stein", Blocks.STONE.defaultBlockState(), RadiationValues.SHIELD_STONE);
        expect("Steinziegel", Blocks.STONE_BRICKS.defaultBlockState(), RadiationValues.SHIELD_STONE);
        expect("Bleiblock", ProtectionRegistry.LEAD_BLOCK.get().defaultBlockState(), RadiationValues.SHIELD_LEAD);
        expect("Grauer Beton", Blocks.GRAY_CONCRETE.defaultBlockState(), RadiationValues.SHIELD_CONCRETE);
        expect("Eisenblock", Blocks.IRON_BLOCK.defaultBlockState(), RadiationValues.SHIELD_STEEL);
        expect("Erde", Blocks.DIRT.defaultBlockState(), RadiationValues.SHIELD_SOIL);
        expect("Glas", Blocks.GLASS.defaultBlockState(), RadiationValues.SHIELD_LIGHT);
        double glow = Shielding.transmission(Blocks.GLOWSTONE.defaultBlockState());
        if (!(glow > RadiationValues.SHIELD_STONE)) throw new GameTestAssertException("Glowstone schirmt wie Stein ab: " + glow);
        double lamp = Shielding.transmission(Blocks.REDSTONE_LAMP.defaultBlockState());
        if (!(lamp > RadiationValues.SHIELD_STONE)) throw new GameTestAssertException("Redstone-Lampe schirmt wie Stein ab: " + lamp);
        h.succeed();
    }

    private static void expect(String name, BlockState st, double want) {
        double got = Shielding.transmission(st);
        if (got != want) throw new GameTestAssertException(name + ": " + got + " statt " + want);
    }

    @GameTest(template = FLOOR, batch = "shower_cap", timeoutTicks = 20)
    public static void showerCapabilityIsFillOnly(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 4, 1);
        h.setBlock(p.above(), Blocks.AIR);
        h.setBlock(p, ProtectionRegistry.SHOWER.get());
        IFluidHandler cap = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p), Direction.UP);
        if (cap == null) throw new GameTestAssertException("Dusche ohne Fluid-Capability");
        int in = cap.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        if (in != 1000) throw new GameTestAssertException("Dusche nimmt kein Wasser an: " + in);
        if (!cap.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty()) throw new GameTestAssertException("Dusche gibt Wasser heraus");
        if (!cap.drain(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE).isEmpty()) {
            throw new GameTestAssertException("Dusche gibt Wasser heraus (nach Sorte)");
        }
        if (cap.getFluidInTank(0).getAmount() != 1000) throw new GameTestAssertException("Tankinhalt: " + cap.getFluidInTank(0).getAmount());
        h.succeed();
    }
}
