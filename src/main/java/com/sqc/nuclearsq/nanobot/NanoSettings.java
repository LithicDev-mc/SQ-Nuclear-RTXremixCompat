package com.sqc.nuclearsq.nanobot;

import com.sqc.nuclearsq.NuclearConfig;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public record NanoSettings(int speedLevel, int countLevel, int reachLevel, double speedFactor, int bots, int maxRadius, int radius, int nearRadius, boolean point) {
    public static final double[] SPEED = {1.0, 2.0, 3.0, 4.0, 5.0, 7.0};
    public static final int[] COUNT = {0, 2, 4, 6, 8, 10};
    public static final int[] REACH = {0, 8, 24, 40, 60, 80};
    public static final int MIN_RADIUS = 4, HARD_MAX_RADIUS = 128;

    public static NanoSettings of(ItemStack canister) {
        int speed = 0, count = 0, reach = 0;
        boolean point = false;
        if (!canister.isEmpty()) {
            ItemContainerContents c = canister.getOrDefault(NanobotRegistry.UPGRADES.get(), ItemContainerContents.EMPTY);
            for (ItemStack s : c.nonEmptyItems()) {
                if (!(s.getItem() instanceof NanoUpgradeItem u)) continue;
                switch (u.type()) {
                    case SPEED -> speed = Math.max(speed, u.level());
                    case COUNT -> count = Math.max(count, u.level());
                    case REACH -> reach = Math.max(reach, u.level());
                    case POINT -> point = true;
                }
            }
        }
        speed = Mth.clamp(speed, 0, NanoUpgradeItem.Type.SPEED.maxLevel);
        count = Mth.clamp(count, 0, NanoUpgradeItem.Type.COUNT.maxLevel);
        reach = Mth.clamp(reach, 0, NanoUpgradeItem.Type.REACH.maxLevel);
        int base = NuclearConfig.value(NuclearConfig.NANOBOT_SWARM_SIZE);
        int bots = Mth.clamp(Math.max(base, COUNT[count]), 1, NanoSwarm.BOTS);
        int cfgRadius = NuclearConfig.value(NuclearConfig.NANOBOT_RADIUS);
        int max = Mth.clamp(cfgRadius + REACH[reach], MIN_RADIUS, HARD_MAX_RADIUS);
        int wanted = canister.isEmpty() ? cfgRadius : canister.getOrDefault(NanobotRegistry.RADIUS.get(), cfgRadius);
        int radius = Mth.clamp(wanted, MIN_RADIUS, max);
        int near = Mth.clamp(Math.min(radius, NuclearConfig.value(NuclearConfig.NANOBOT_NEAR_RADIUS) + REACH[reach] / 2), 3, HARD_MAX_RADIUS);
        return new NanoSettings(speed, count, reach, SPEED[speed], bots, max, radius, near, point);
    }

    public static NanoSettings forPlayer(Player player) {
        return of(canisterOf(player));
    }

    public static ItemStack canisterOf(Player player) {
        if (player.getMainHandItem().getItem() instanceof NanoCanisterItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof NanoCanisterItem) return player.getOffhandItem();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof NanoCanisterItem) return inv.getItem(i);
        }
        return ItemStack.EMPTY;
    }

    public double workSpeed() {
        return speedFactor;
    }
}
