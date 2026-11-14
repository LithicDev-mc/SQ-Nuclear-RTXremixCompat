package com.sqc.nuclearsq.exo;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ExoArmorItem extends ArmorItem {
    public ExoArmorItem(Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player p) || p.getItemBySlot(getType().getSlot()) != stack) return;
        if (getType() == Type.CHESTPLATE && p.tickCount % 5 == 0 && p instanceof ServerPlayer sp) ExoMagnet.pull(sp);
        if (p.tickCount % 20 != 0) return;
        List<ModuleEntry> mods = stack.getOrDefault(ExoRegistry.MODULES.get(), List.of());
        int cost = 0;
        for (ModuleEntry m : mods) if (!(ExoModule.of(m.id()) instanceof ExoModule x && x.kind() == ExoModule.Kind.BATTERY)) cost += ExoModule.COST_PER_LEVEL * m.level();
        boolean on = cost > 0 && ExoCoreItem.drain(p, cost);
        if (stack.getOrDefault(ExoRegistry.ACTIVE.get(), false) != on) stack.set(ExoRegistry.ACTIVE.get(), on);
        if (on && getType() == Type.HELMET && p instanceof ServerPlayer sp) {
            int ore = ExoEvents.level(sp, ExoModule.Kind.ORE);
            if (ore > 0) ExoOre.show(sp, ore);
        }
    }
}
