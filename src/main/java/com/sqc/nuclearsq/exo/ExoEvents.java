package com.sqc.nuclearsq.exo;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class ExoEvents {
    private ExoEvents() {}

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent e) {
        ItemStack s = e.getItemStack();
        if (!(s.getItem() instanceof ExoArmorItem a) || !s.getOrDefault(ExoRegistry.ACTIVE.get(), false)) return;
        List<ModuleEntry> list = s.getOrDefault(ExoRegistry.MODULES.get(), List.of());
        for (int i = 0; i < list.size(); i++) {
            ExoModule m = ExoModule.of(list.get(i).id());
            if (m == null || m.kind().attribute == null || m.kind().piece != a.getType()) continue;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "exo_" + m.kind().id + "_" + i);
            e.addModifier(m.kind().attribute, new AttributeModifier(id, m.kind().amount(m.level()), m.kind().op), EquipmentSlotGroup.bySlot(a.getType().getSlot()));
        }
    }

    public static int level(LivingEntity e, ExoModule.Kind kind) {
        ItemStack s = e.getItemBySlot(kind.piece.getSlot());
        if (!(s.getItem() instanceof ExoArmorItem) || !s.getOrDefault(ExoRegistry.ACTIVE.get(), false)) return 0;
        int n = 0;
        for (ModuleEntry me : s.getOrDefault(ExoRegistry.MODULES.get(), List.<ModuleEntry>of())) {
            ExoModule m = ExoModule.of(me.id());
            if (m != null && m.kind() == kind) n += m.level();
        }
        return n;
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent e) {
        if (!e.getSource().is(DamageTypeTags.IS_FIRE)) return;
        int lvl = level(e.getEntity(), ExoModule.Kind.FIRE);
        if (lvl <= 0) return;
        float reduced = fireDamage(e.getAmount(), lvl);
        if (reduced <= 0f) e.setCanceled(true);
        else e.setAmount(reduced);
    }

    public static float fireDamage(float amount, int level) {
        return (float) (amount * Math.max(0.0, 1.0 - ExoModule.Kind.FIRE.amount(level)));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof Player p)) return;
        int lvl = level(p, ExoModule.Kind.LOOT);
        if (lvl > 0) for (ItemEntity d : e.getDrops()) bonus(d.getItem(), p, lvl);
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent e) {
        if (!(e.getBreaker() instanceof Player p)) return;
        int lvl = level(p, ExoModule.Kind.LUCK);
        if (lvl > 0) for (ItemEntity d : e.getDrops()) if (d.getItem().getItem() != e.getState().getBlock().asItem()) bonus(d.getItem(), p, lvl);
    }

    private static void bonus(ItemStack stack, Player p, int lvl) {
        if (stack.getMaxStackSize() > 1) stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() + p.getRandom().nextInt(lvl + 1)));
    }
}
