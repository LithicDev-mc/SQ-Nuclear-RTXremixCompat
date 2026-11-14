package com.sqc.nuclearsq.particle;

import com.sqc.nuclearsq.NuclearSQ;

import java.util.EnumMap;
import java.util.Map;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ParticleRegistry {
    private ParticleRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);
    public static final Map<ParticleClasses.Upgrade, DeferredItem<ParticleUpgradeItem>> UPGRADES = new EnumMap<>(ParticleClasses.Upgrade.class);

    public static final java.util.List<DeferredItem<ParticleClassCardItem>> CARDS = new java.util.ArrayList<>();

    static {
        for (ParticleClasses.Upgrade u : ParticleClasses.Upgrade.values()) {
            UPGRADES.put(u, ITEMS.registerItem("particle_upgrade_" + u.key + "_sqc", p -> new ParticleUpgradeItem(u, p.stacksTo(16))));
        }
        for (int i = 0; i < ParticleClasses.CARDS.size(); i++) {
            int tier = i + 1;
            CARDS.add(ITEMS.registerItem("particle_card_" + ParticleClasses.CARDS.get(i) + "_sqc", p -> new ParticleClassCardItem(tier, p.stacksTo(1))));
        }
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
