package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.exo.ExoArmorItem;
import com.sqc.nuclearsq.exo.ExoRegistry;
import com.sqc.nuclearsq.exo.ModuleEntry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ExoArmorTests {
    private ExoArmorTests() {}

    @GameTest(template = "floor", batch = "exo_armor", timeoutTicks = 20)
    public static void exoArmorBeatsNetherite(GameTestHelper h) {
        Item[] netherite = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        for (int k = 0; k < netherite.length; k++) {
            ExoArmorItem exo = ExoRegistry.ARMOR.get(k).get();
            ArmorItem n = (ArmorItem) netherite[k];
            if (exo.getType() != n.getType()) throw new GameTestAssertException("Reihenfolge der Exo-Teile passt nicht");
            if (exo.getDefense() != n.getDefense() + 1)
                throw new GameTestAssertException(exo.getType() + ": Ruestung " + exo.getDefense() + ", erwartet " + (n.getDefense() + 1));
            if (exo.getToughness() != n.getToughness())
                throw new GameTestAssertException(exo.getType() + ": Haerte " + exo.getToughness() + ", erwartet " + n.getToughness());
            if (exo.getMaterial().value().knockbackResistance() != n.getMaterial().value().knockbackResistance())
                throw new GameTestAssertException(exo.getType() + ": Rueckstossresistenz passt nicht zu Netherit");
        }
        h.succeed();
    }

    private static ItemStack anvil(GameTestHelper h, ItemStack left, ItemStack right) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        AnvilMenu menu = new AnvilMenu(0, p.getInventory());
        menu.getSlot(0).set(left);
        menu.getSlot(1).set(right);
        return menu.getSlot(2).getItem().copy();
    }

    private static ItemStack damaged(int k) {
        ItemStack s = new ItemStack(ExoRegistry.ARMOR.get(k).get());
        s.setDamageValue(s.getMaxDamage() - 10);
        s.set(ExoRegistry.MODULES.get(), List.of(new ModuleEntry("strength", 1)));
        return s;
    }

    private static void keepsModules(ItemStack out, String what) {
        if (!out.getOrDefault(ExoRegistry.MODULES.get(), List.<ModuleEntry>of()).equals(List.of(new ModuleEntry("strength", 1))))
            throw new GameTestAssertException(what + ": Module gingen verloren");
    }

    @GameTest(template = "floor", batch = "exo_armor", timeoutTicks = 20)
    public static void exoRepairWithSteelSheet(GameTestHelper h) {
        Item steel = h.getLevel().registryAccess().registryOrThrow(Registries.ITEM).get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "steel_sheet_sqc"));
        if (steel == null || steel == Items.AIR) throw new GameTestAssertException("Stahlblech fehlt");
        for (int k = 0; k < ExoRegistry.ARMOR.size(); k++) {
            ItemStack in = damaged(k);
            ItemStack out = anvil(h, in.copy(), new ItemStack(steel, 4));
            if (out.isEmpty()) throw new GameTestAssertException(in.getItem() + ": Amboss repariert nicht mit Stahlblech");
            if (out.getDamageValue() >= in.getDamageValue()) throw new GameTestAssertException(in.getItem() + ": Haltbarkeit steigt nicht");
            keepsModules(out, "Stahlblech");
        }
        h.succeed();
    }

    @GameTest(template = "floor", batch = "exo_armor", timeoutTicks = 20)
    public static void exoRepairWithSecondPiece(GameTestHelper h) {
        for (int k = 0; k < ExoRegistry.ARMOR.size(); k++) {
            ItemStack in = damaged(k);
            ItemStack out = anvil(h, in.copy(), damaged(k));
            if (out.isEmpty()) throw new GameTestAssertException(in.getItem() + ": Amboss verbindet zwei gleiche Teile nicht");
            if (out.getDamageValue() >= in.getDamageValue()) throw new GameTestAssertException(in.getItem() + ": Haltbarkeit steigt nicht");
            keepsModules(out, "Zweites Teil");
        }
        h.succeed();
    }

    @GameTest(template = "floor", batch = "exo_armor", timeoutTicks = 20)
    public static void exoArmorTakesMending(GameTestHelper h) {
        List<TagKey<Item>> tags = List.of(ItemTags.HEAD_ARMOR, ItemTags.CHEST_ARMOR, ItemTags.LEG_ARMOR, ItemTags.FOOT_ARMOR);
        Holder<Enchantment> mending = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING);
        for (int k = 0; k < ExoRegistry.ARMOR.size(); k++) {
            ItemStack in = damaged(k);
            if (!in.is(tags.get(k))) throw new GameTestAssertException(in.getItem() + ": fehlt im Tag " + tags.get(k).location());
            ItemStack out = anvil(h, in.copy(), EnchantedBookItem.createForEnchantment(new EnchantmentInstance(mending, 1)));
            if (out.isEmpty() || EnchantmentHelper.getItemEnchantmentLevel(mending, out) != 1)
                throw new GameTestAssertException(in.getItem() + ": Reparatur-Buch laesst sich nicht auftragen");
            keepsModules(out, "Reparatur-Buch");
        }
        h.succeed();
    }
}
