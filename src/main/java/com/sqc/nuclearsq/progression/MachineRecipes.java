package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.EquipmentRecipe;
import com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.particle.ParticleClasses;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class MachineRecipes {
    private MachineRecipes() {}

    public record Row(List<RecipeSources.Need> needs, List<ReprocessingRecipe.Result> results, int time, int energyPerTick,
                      List<Component> notes) {}

    public static boolean has(BigMachine m) {
        if (m.recipeKind() != null) return true;
        return switch (m) {
            case REACTOR_ASSEMBLER, COMPUTE_ASSEMBLER, SMALL_PARTS_ASSEMBLER, MICRO_ASSEMBLER, CLEAN_ROOM, EQUIPMENT_ASSEMBLER,
                 HEAVY_ASSEMBLER, PARTICLE_ASSEMBLER, COMPONENT_ASSEMBLER, QUANTUM_COMPUTER -> true;
            default -> false;
        };
    }

    public static List<Row> rows(BigMachine m, RecipeManager recipes, HolderLookup.Provider regs) {
        List<Row> out = new ArrayList<>();
        if (m == BigMachine.PARTICLE_ASSEMBLER) {
            particle(out);
            return out;
        }
        if (!has(m)) return out;
        for (var holder : recipes.getRecipes()) {
            Recipe<?> r = holder.value();
            if (makes(m, r)) add(out, r, regs);
        }
        out.sort(Comparator.comparing(MachineRecipes::sortKey));
        return out;
    }

    public static boolean makes(BigMachine m, Recipe<?> r) {
        if (r instanceof ReprocessingRecipe rr) return rr.kind() == m.recipeKind();
        boolean auto = m.isAutocrafter();
        if (r instanceof EquipmentRecipe) return m == BigMachine.EQUIPMENT_ASSEMBLER;
        if (r instanceof HeavyAssemblyRecipe) return m == BigMachine.HEAVY_ASSEMBLER;
        if (r instanceof SmallPartsRecipe s) {
            if (auto) return true;
            boolean micro = s.result().is(BigCrafter.MICRO_PRODUCTS), clean = s.result().is(BigCrafter.CLEAN_ROOM_PRODUCTS);
            return m == BigMachine.SMALL_PARTS_ASSEMBLER && !micro && !clean
                    || m == BigMachine.MICRO_ASSEMBLER && micro && !clean
                    || m == BigMachine.CLEAN_ROOM && clean && !micro;
        }
        if (r instanceof AssemblyRecipe) return auto || m == BigMachine.REACTOR_ASSEMBLER || m == BigMachine.COMPUTE_ASSEMBLER;
        return auto && r.getType() == RecipeType.CRAFTING;
    }

    private static String sortKey(Row r) {
        if (r.results().isEmpty()) return "";
        return r.results().get(0).stack().getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    private static void add(List<Row> out, Recipe<?> r, HolderLookup.Provider regs) {
        if (r instanceof ReprocessingRecipe rr) {
            List<RecipeSources.Need> in = new ArrayList<>();
            in.add(new RecipeSources.Need(rr.ingredient(), rr.count()));
            if (rr.hasAux()) in.add(new RecipeSources.Need(rr.aux(), rr.auxCount()));
            List<Component> notes = new ArrayList<>();
            for (ReprocessingRecipe.FluidCost c : rr.fluids())
                notes.add(Component.translatable("jei.nuclearsq.fluid_in", c.amount(), c.port()));
            for (ReprocessingRecipe.FluidOut o : rr.fluidResults()) {
                Fluid f = BuiltInRegistries.FLUID.get(o.fluid());
                if (f == Fluids.EMPTY) continue;
                notes.add(Component.translatable("gui.nuclearsq.term.recipes.fluid_out", o.amount(), f.getFluidType().getDescription(), o.port()));
            }
            out.add(new Row(in, rr.results(), rr.time(), rr.energyPerTick(), notes));
            return;
        }
        ItemStack res = r.getResultItem(regs);
        if (res.isEmpty()) return;
        RecipeSources.Source s = RecipeSources.of(r, regs, res.getItem());
        if (s == null) return;
        int time = 0, fe = 0;
        if (r instanceof AssemblyRecipe a) { time = a.time(); fe = a.energyPerTick(); }
        else if (r instanceof SmallPartsRecipe p) { time = p.time(); fe = p.energyPerTick(); }
        else if (r instanceof EquipmentRecipe e) { time = e.base().time(); fe = e.base().energyPerTick(); }
        else if (r instanceof HeavyAssemblyRecipe hv) { time = hv.time(); fe = hv.energyPerTick(); }
        out.add(new Row(s.needs(), List.of(new ReprocessingRecipe.Result(res, 1f)), time, fe, List.of()));
    }

    private static void particle(List<Row> out) {
        ParticleClasses pc = ParticleClasses.get();
        double classSum = 0;
        for (ParticleClasses.ItemClass c : pc.classes) classSum += c.weight();
        for (ParticleClasses.ItemClass c : pc.classes) {
            List<ParticleClasses.Entry> live = c.entries().stream().filter(e -> ParticleClasses.resolve(e.item()) != null).toList();
            double sum = live.stream().mapToInt(ParticleClasses.Entry::weight).sum();
            for (ParticleClasses.Entry e : live) {
                Item it = ParticleClasses.resolve(e.item());
                double pct = classSum * sum == 0 ? 0 : 100.0 * c.weight() / classSum * e.weight() / sum;
                out.add(new Row(List.of(), List.of(new ReprocessingRecipe.Result(new ItemStack(it, Math.max(1, e.max())), 1f)), 0, 0, List.of(
                        Component.translatable("jei.nuclearsq.particle.class", Component.translatable("jei.nuclearsq.particle.class." + c.id())),
                        Component.translatable("jei.nuclearsq.particle.count", e.min(), e.max()),
                        Component.translatable("jei.nuclearsq.particle.share", String.format("%.1f", pct)))));
            }
        }
    }
}
