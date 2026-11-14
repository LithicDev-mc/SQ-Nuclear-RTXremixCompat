package com.sqc.nuclearsq.turbine;

import com.sqc.nuclearsq.registry.MultiblockRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class TurbineMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    private final ContainerData data;

    public TurbineMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new SimpleContainerData(TurbineControllerBlockEntity.DATA_COUNT));
    }

    public TurbineMenu(int id, Inventory inv, BlockPos pos, ContainerData data) {
        super(MultiblockRegistry.TURBINE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }

    private int u(int i) { return data.get(i) & 0xFFFF; }

    public boolean formed() { return u(0) == 1; }
    public boolean running() { return u(1) == 1; }
    public int throttle() { return u(2); }
    public int rpm() { return u(3); }
    public int steam() { return u(4) | (u(5) << 16); }
    public int energy() { return u(6) | (u(7) << 16); }
    public int feNow() { return u(8) | (u(15) << 16); }
    public int mbNow() { return u(9); }
    public int temp() { return u(10); }
    public int matched() { return u(11); }
    public int total() { return u(12); }
    public int output() { return u(13) | (u(16) << 16); }
    public int shaft() { return u(14) | (u(17) << 16); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return com.sqc.nuclearsq.data.RemoteAccess.inReach(player, pos);
    }
}
