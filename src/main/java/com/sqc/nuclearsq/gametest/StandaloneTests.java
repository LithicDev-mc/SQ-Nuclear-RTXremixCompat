package com.sqc.nuclearsq.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.FuelChargeIngredient;
import com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class StandaloneTests {
    private StandaloneTests() {}

    private static final String FLOOR = "floor";

    private static final Set<String> REQUIRED_OK = Set.of("neoforge", "minecraft", "geckolib", "sablecompanion");

    @GameTest(template = FLOOR, batch = "zz_standalone", timeoutTicks = 20)
    public static void noForeignModIsRequired(GameTestHelper h) {
        var info = ModList.get().getModContainerById(NuclearSQ.MODID).orElseThrow().getModInfo();
        List<String> bad = new ArrayList<>();
        for (var dep : info.getDependencies())
            if (dep.getType() == net.neoforged.neoforgespi.language.IModInfo.DependencyType.REQUIRED && !REQUIRED_OK.contains(dep.getModId())) bad.add(dep.getModId());
        if (!bad.isEmpty()) throw new GameTestAssertException("Pflicht-Mods in neoforge.mods.toml: " + bad);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "zz_standalone", timeoutTicks = 20)
    public static void everyIngredientHasItems(GameTestHelper h) {
        List<String> bad = new ArrayList<>();
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            var r = holder.value();
            String id = holder.id().toString();
            if (r instanceof AssemblyRecipe a) a.ingredients().forEach(n -> check(bad, id, n.ingredient()));
            else if (r instanceof SmallPartsRecipe s) s.ingredients().forEach(n -> check(bad, id, n.ingredient()));
            else if (r instanceof HeavyAssemblyRecipe hv) hv.key().values().forEach(i -> check(bad, id, i));
            else if (r instanceof ReprocessingRecipe rp) check(bad, id, rp.ingredient());
        }
        if (!bad.isEmpty()) throw new GameTestAssertException(bad.size() + " leere Zutaten: " + String.join("; ", bad));
        h.succeed();
    }

    private static void check(List<String> bad, String recipe, Ingredient ing) {
        if (ing.getCustomIngredient() instanceof FuelChargeIngredient) return;
        if (ing.hasNoItems()) bad.add(recipe);
    }
}
