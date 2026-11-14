package com.sqc.nuclearsq.robotics;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class RodGripperMenu extends AbstractContainerMenu {
    public static final int BTN_TOGGLE = 0, WIDTH = 212, HEIGHT = 190;

    @Nullable private final RodGripperBlockEntity gripper;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public RodGripperMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, null, new SimpleContainerData(RodGripperBlockEntity.DATA_SIZE), ContainerLevelAccess.create(inv.player.level(), pos));
    }

    public RodGripperMenu(int id, Inventory inv, RodGripperBlockEntity gripper, ContainerData data) {
        this(id, inv, gripper, data, ContainerLevelAccess.create(gripper.getLevel(), gripper.getBlockPos()));
    }

    private RodGripperMenu(int id, Inventory inv, @Nullable RodGripperBlockEntity gripper, ContainerData data, ContainerLevelAccess access) {
        super(RoboticsRegistry.GRIPPER_MENU.get(), id);
        this.gripper = gripper;
        this.data = data;
        this.access = access;
        addDataSlots(data);
    }

    public int data(int i) {
        return data.get(i);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (gripper == null || id != BTN_TOGGLE) return false;
        gripper.setEnabled(!gripper.enabled());
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return gripper == null || stillValid(access, player, gripper.getBlockState().getBlock());
    }
}
