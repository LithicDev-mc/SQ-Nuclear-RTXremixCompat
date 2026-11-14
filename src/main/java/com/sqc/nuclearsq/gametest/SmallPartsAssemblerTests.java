package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.kit.KitRegistry;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.machine.big.BigCrafter;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;
import com.sqc.nuclearsq.registry.SmallPartsRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class SmallPartsAssemblerTests {
    private SmallPartsAssemblerTests() {}

    private static final String FLOOR = "floor";
    private static final BigMachine M = BigMachine.SMALL_PARTS_ASSEMBLER;

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static BigMachineBlockEntity build(GameTestHelper h, BlockPos min) {
        BlockPos ctrl = h.absolutePos(min).subtract(M.structure.world(BlockPos.ZERO, 0, 0, 0, 0));
        for (MultiblockStructure.Cell c : M.structure.cells(ctrl, 0)) h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(ctrl) instanceof BigMachineBlockEntity be)) throw new GameTestAssertException("Controller ohne BlockEntity");
        return be;
    }

    private static void charge(BigMachineBlockEntity be) {
        while (be.energy().receiveEnergy(1000, false) > 0) { }
    }

    @GameTest(template = FLOOR, batch = "small_parts_assembler", timeoutTicks = 400)
    public static void smallPartsAssemblerBuildsReactorAssemblerKit(GameTestHelper h) {
        BigMachineBlockEntity be = build(h, new BlockPos(2, 2, 2));
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Kleinteil-Assembler wird nicht als aufgebaut erkannt");
            BigCrafter c = be.crafter();
            be.items().insertItem(0, new ItemStack(Items.IRON_INGOT, 6), false);
            be.items().insertItem(1, new ItemStack(Items.REDSTONE, 4), false);
            be.items().insertItem(2, new ItemStack(Items.COPPER_INGOT, 4), false);
            be.items().insertItem(3, new ItemStack(Items.CRAFTING_TABLE), false);
            be.items().insertItem(4, new ItemStack(Items.BLAST_FURNACE), false);
            charge(be);
            h.runAfterDelay(5, () -> {
                if (c.partsRecipe() == null) throw new GameTestAssertException("Rezept fuer das Reaktor-Assembler-Kit nicht erkannt");
                if (c.energyPerTick() != 480 * 91 / 100) throw new GameTestAssertException("436 FE/t (480 FE/t mal Rechenstufe 0 mit 91 %) erwartet, ist " + c.energyPerTick());
                h.runAfterDelay(M.extra("craft_ticks") * 100 / 91 + 20, () -> {
                    ItemStack out = be.items().getStackInSlot(9);
                    if (!out.is(KitRegistry.KITS.get("reactor_assembler").get())) throw new GameTestAssertException("Kein Reaktor-Assembler-Kit im Ergebnisfeld: " + out);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "small_parts_assembler", timeoutTicks = 300)
    public static void smallPartsAssemblerCrafts(GameTestHelper h) {
        var kit = h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "small_parts_assembler_kit_sqc"));
        if (kit.isEmpty() || !kit.get().value().getResultItem(h.getLevel().registryAccess()).is(KitRegistry.KITS.get("small_parts_assembler").get())) {
            throw new GameTestAssertException("Werkbank-Rezept liefert nicht das Kit");
        }
        var parts = kit.get().value().getIngredients();
        long iron = parts.stream().filter(i -> i.test(new ItemStack(Items.IRON_BLOCK))).count();
        long red = parts.stream().filter(i -> i.test(new ItemStack(Items.REDSTONE_BLOCK))).count();
        if (iron != 4 || red != 2) throw new GameTestAssertException("Kit-Rezept: 4 Eisen- und 2 Redstone-Bloecke erwartet, sind " + iron + "/" + red);
        if (h.getLevel().getRecipeManager().getAllRecipesFor(SmallPartsRegistry.TYPE.get()).isEmpty()) {
            throw new GameTestAssertException("Keine Rezepte nuclearsq:small_parts_assembly geladen");
        }
        BigMachineBlockEntity be = build(h, new BlockPos(2, 2, 2));
        h.runAfterDelay(25, () -> {
            if (!be.isFormed()) throw new GameTestAssertException("Kleinteil-Assembler wird nicht als aufgebaut erkannt");
            BigCrafter c = be.crafter();
            if (c == null || be.items() == null || be.items().getSlots() != 10) throw new GameTestAssertException("Kein 3x3-Raster mit Ergebnisfeld");
            if (c.status() != BigCrafter.NO_INPUT) throw new GameTestAssertException("leer: NO_INPUT erwartet, ist " + c.status());
            be.items().insertItem(0, new ItemStack(Items.IRON_INGOT, 1), false);
            be.items().insertItem(5, new ItemStack(Items.IRON_INGOT, 1), false);
            be.items().insertItem(7, new ItemStack(Items.REDSTONE, 4), false);
            be.items().insertItem(2, new ItemStack(Items.COPPER_INGOT, 2), false);
            be.items().insertItem(4, new ItemStack(item("nuclearsq:control_board_1_sqc")), false);
            be.items().insertItem(8, new ItemStack(Items.DIAMOND), false);
            h.runAfterDelay(5, () -> {
                if (c.status() != BigCrafter.NO_INPUT) throw new GameTestAssertException("fremdes Item im Raster: kein Rezept erwartet, ist " + c.status());
                be.items().extractItem(8, 1, false);
                h.runAfterDelay(5, () -> {
                    if (c.status() != BigCrafter.NO_POWER) throw new GameTestAssertException("ohne Strom: NO_POWER erwartet, ist " + c.status());
                    if (c.partsRecipe() == null) throw new GameTestAssertException("Rezept nicht erkannt");
                    charge(be);
                    int full = be.energy().getEnergyStored();
                    h.runAfterDelay(M.extra("craft_ticks") * 100 / 91 + 20, () -> {
                        ItemStack out = be.items().getStackInSlot(9);
                        if (!out.is(item("nuclearsq:hard_drive_1_sqc"))) throw new GameTestAssertException("Keine Festplatte im Ergebnisfeld: " + out);
                        for (int i = 0; i < 9; i++) {
                            if (!be.items().getStackInSlot(i).isEmpty()) throw new GameTestAssertException("Feld " + i + " nicht verbraucht");
                        }
                        if (be.energy().getEnergyStored() >= full) throw new GameTestAssertException("kein Strom verbraucht");
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = FLOOR, batch = "small_parts_assembler_kit", timeoutTicks = 60)
    public static void smallPartsAssemblerKitPlaces(GameTestHelper h) {
        MachineKits.Placement pl = MachineKits.placement(MachineKits.def("small_parts_assembler"), h.absolutePos(new BlockPos(7, 4, 4)), Direction.SOUTH);
        if (!MachineKits.blocked(h.getLevel(), pl, null).isEmpty()) throw new GameTestAssertException("Platz gilt als belegt");
        MachineKits.place(h.getLevel(), pl);
        h.runAfterDelay(10, () -> {
            if (!(h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity be) || be.machine() != M) {
                throw new GameTestAssertException("Kein Kleinteil-Assembler aufgestellt");
            }
            if (!be.isFormed()) throw new GameTestAssertException("Kleinteil-Assembler nach dem Aufstellen nicht fertig");
            BlockPos part = null;
            for (MultiblockStructure.Cell c : pl.cells()) if (!c.pos().equals(pl.anchor())) { part = c.pos(); break; }
            FakePlayerFactory.getMinecraft(h.getLevel()).gameMode.destroyBlock(part);
            boolean dropped = !h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pl.anchor()).inflate(6),
                    e -> e.getItem().is(KitRegistry.KITS.get("small_parts_assembler").get())).isEmpty();
            if (!dropped) throw new GameTestAssertException("Kit faellt nicht heraus");
            if (h.getLevel().getBlockEntity(pl.anchor()) instanceof BigMachineBlockEntity) throw new GameTestAssertException("Anlage steht noch");
            h.succeed();
        });
    }
}
