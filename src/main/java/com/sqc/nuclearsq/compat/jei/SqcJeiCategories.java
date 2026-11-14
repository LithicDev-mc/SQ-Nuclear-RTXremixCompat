package com.sqc.nuclearsq.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.assembler.AssemblyRecipe;
import com.sqc.nuclearsq.assembler.HeavyAssemblyRecipe;
import com.sqc.nuclearsq.assembler.SmallPartsRecipe;
import com.sqc.nuclearsq.particle.ParticleClasses;
import com.sqc.nuclearsq.reprocessing.ReprocessingRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

final class SqcJeiCategories {
    private SqcJeiCategories() {}

    private static final int GREY = 0xFF808080;

    private static Component t(String key, Object... args) {
        return Component.translatable("jei.nuclearsq." + key, args);
    }

    private static void line(GuiGraphics g, Component c, int x, int y) {
        g.drawString(Minecraft.getInstance().font, c, x, y, GREY, false);
    }

    private static String seconds(int ticks) {
        return String.format("%.1f", ticks / 20.0);
    }

    private static void timeAndEnergy(GuiGraphics g, int time, int fe, int x, int y) {
        if (time > 0) line(g, t("time", seconds(time)), x, y);
        if (fe > 0) line(g, t("energy", fe), x, y + 10);
    }

    private static List<ItemStack> counted(Ingredient ing, int count) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : ing.getItems()) out.add(s.copyWithCount(count));
        if (out.isEmpty()) out.add(new ItemStack(Items.BARRIER));
        return out;
    }

    private abstract static class Base<T> implements IRecipeCategory<T> {
        final IGuiHelper gui;
        final RecipeType<T> type;
        final Component title;
        final IDrawable icon;
        final int w, h;

        Base(IGuiHelper gui, RecipeType<T> type, Component title, ItemStack icon, int w, int h) {
            this.gui = gui;
            this.type = type;
            this.title = title;
            this.icon = gui.createDrawableItemStack(icon);
            this.w = w;
            this.h = h;
        }

        @Override public RecipeType<T> getRecipeType() { return type; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return w; }
        @Override public int getHeight() { return h; }
    }

    record Needs(List<Ingredient> ingredients, List<Integer> counts, ItemStack result, int time, int fe) {}

    static Needs needs(AssemblyRecipe r) {
        return new Needs(r.ingredients().stream().map(AssemblyRecipe.Need::ingredient).toList(),
                r.ingredients().stream().map(AssemblyRecipe.Need::count).toList(), r.result(), r.time(), r.energyPerTick());
    }

    static Needs needs(SmallPartsRecipe r) {
        return new Needs(r.ingredients().stream().map(SmallPartsRecipe.Need::ingredient).toList(),
                r.ingredients().stream().map(SmallPartsRecipe.Need::count).toList(), r.result(), r.time(), r.energyPerTick());
    }

    static final class Formless extends Base<Needs> {
        Formless(IGuiHelper gui, RecipeType<Needs> type, String madeIn, ItemStack icon) {
            super(gui, type, madeIn.equals("micro_assembler") ? t("micro") : Component.translatable("compendium.made_in." + madeIn), icon, 150, 62);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Needs r, IFocusGroup f) {
            for (int i = 0; i < r.ingredients().size(); i++) {
                b.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 3) * 18, 1 + (i / 3) * 18)
                        .addItemStacks(counted(r.ingredients().get(i), r.counts().get(i)));
            }
            b.addSlot(RecipeIngredientRole.OUTPUT, 114, 19).setOutputSlotBackground().addItemStack(r.result());
        }

        @Override
        public void draw(Needs r, IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            gui.getRecipeArrow().draw(g, 80, 19);
            timeAndEnergy(g, r.time(), r.fe(), 60, 42);
        }
    }

    static final class Heavy extends Base<HeavyAssemblyRecipe> {
        Heavy(IGuiHelper gui, RecipeType<HeavyAssemblyRecipe> type, ItemStack icon) {
            super(gui, type, Component.translatable("compendium.made_in.heavy_assembler"), icon, 150, 94);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, HeavyAssemblyRecipe r, IFocusGroup f) {
            for (int y = 0; y < r.height(); y++) {
                for (int x = 0; x < r.width(); x++) {
                    char c = r.pattern().get(y).charAt(x);
                    if (c == ' ') continue;
                    Ingredient ing = r.key().get(String.valueOf(c));
                    b.addSlot(RecipeIngredientRole.INPUT, 1 + x * 18, 1 + y * 18).addItemStacks(counted(ing, 1));
                }
            }
            b.addSlot(RecipeIngredientRole.OUTPUT, 128, 19).setOutputSlotBackground().addItemStack(r.result());
        }

        @Override
        public void draw(HeavyAssemblyRecipe r, IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            gui.getRecipeArrow().draw(g, 100, 19);
            timeAndEnergy(g, r.time(), r.energyPerTick(), 96, 46);
        }
    }

    static final class Machine extends Base<RecipeHolder<ReprocessingRecipe>> {
        Machine(IGuiHelper gui, RecipeType<RecipeHolder<ReprocessingRecipe>> type, ReprocessingRecipe.Kind kind, ItemStack icon) {
            super(gui, type, Component.translatable("compendium.made_in." + kind.id), icon, 150, 66);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, RecipeHolder<ReprocessingRecipe> h, IFocusGroup f) {
            ReprocessingRecipe r = h.value();
            b.addSlot(RecipeIngredientRole.INPUT, 1, 1).addItemStacks(counted(r.ingredient(), r.count()));
            if (r.hasAux()) b.addSlot(RecipeIngredientRole.INPUT, 1, 21).addItemStacks(counted(r.aux(), r.auxCount()));
            int n = 0;
            for (ReprocessingRecipe.Result res : r.results()) {
                IRecipeSlotBuilder s = b.addSlot(RecipeIngredientRole.OUTPUT, 80 + (n % 2) * 18, 1 + (n / 2) * 18)
                        .setOutputSlotBackground().addItemStack(res.stack());
                if (res.chance() < 1f) {
                    float chance = res.chance();
                    s.addRichTooltipCallback((view, tip) -> tip.add(t("chance", Math.round(chance * 1000) / 10.0)));
                }
                n++;
            }
            int fluids = 0;
            for (ReprocessingRecipe.FluidOut out : r.fluidResults()) {
                Fluid fluid = BuiltInRegistries.FLUID.get(out.fluid());
                if (fluid == Fluids.EMPTY) continue;
                b.addSlot(RecipeIngredientRole.OUTPUT, 118 + (fluids % 2) * 18, 1 + (fluids / 2) * 18).setOutputSlotBackground()
                        .setFluidRenderer(out.amount(), false, 16, 16).addFluidStack(fluid, out.amount());
                fluids++;
            }
        }

        @Override
        public void draw(RecipeHolder<ReprocessingRecipe> h, IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            ReprocessingRecipe r = h.value();
            gui.getRecipeArrow().draw(g, 40, 10);
            if (r.time() > 0) line(g, t("time", seconds(r.time())), 1, 42);
            if (r.energyPerTick() > 0) line(g, t("energy", r.energyPerTick()), 75, 42);
            int y = 52;
            for (ReprocessingRecipe.FluidCost c : r.fluids()) {
                line(g, t("fluid_in", c.amount(), c.port()), 1, y);
                y += 10;
            }
        }
    }

    record Drop(String cls, ItemStack stack, int min, int max, double percent) {}

    static List<Drop> particleDrops() {
        List<Drop> out = new ArrayList<>();
        ParticleClasses pc = ParticleClasses.get();
        double classSum = 0;
        for (ParticleClasses.ItemClass c : pc.classes) classSum += c.weight();
        for (ParticleClasses.ItemClass c : pc.classes) {
            List<ParticleClasses.Entry> live = c.entries().stream().filter(e -> ParticleClasses.resolve(e.item()) != null).toList();
            double sum = live.stream().mapToInt(ParticleClasses.Entry::weight).sum();
            for (ParticleClasses.Entry e : live) {
                out.add(new Drop(c.id(), new ItemStack(ParticleClasses.resolve(e.item())), e.min(), e.max(),
                        classSum * sum == 0 ? 0 : 100.0 * c.weight() / classSum * e.weight() / sum));
            }
        }
        return out;
    }

    static final class Particle extends Base<Drop> {
        Particle(IGuiHelper gui, RecipeType<Drop> type, ItemStack icon) {
            super(gui, type, t("particle"), icon, 150, 36);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder b, Drop d, IFocusGroup f) {
            b.addSlot(RecipeIngredientRole.OUTPUT, 1, 9).setOutputSlotBackground().addItemStack(d.stack().copyWithCount(Math.max(1, d.max())));
        }

        @Override
        public void draw(Drop d, IRecipeSlotsView v, GuiGraphics g, double mx, double my) {
            line(g, t("particle.class", t("particle.class." + d.cls())), 24, 2);
            line(g, t("particle.count", d.min(), d.max()), 24, 13);
            line(g, t("particle.share", String.format("%.1f", d.percent())), 24, 24);
        }
    }
}
