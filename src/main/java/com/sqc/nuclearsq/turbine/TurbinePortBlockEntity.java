package com.sqc.nuclearsq.turbine;

import com.sqc.nuclearsq.registry.MultiblockRegistry;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class TurbinePortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controllerPos;

    public TurbinePortBlockEntity(BlockPos pos, BlockState state) {
        super(MultiblockRegistry.TURBINE_PORT_BE.get(), pos, state);
    }

    public TurbinePortType type() {
        return ((TurbinePortBlock) getBlockState().getBlock()).type();
    }

    public void setController(@Nullable BlockPos pos) {
        BlockPos next = pos == null ? null : pos.immutable();
        if (java.util.Objects.equals(controllerPos, next)) return;
        controllerPos = next;
        setChanged();
        if (level != null && !level.isClientSide) level.invalidateCapabilities(worldPosition);
    }

    @Nullable
    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Nullable
    public TurbineControllerBlockEntity findController() {
        if (level == null || controllerPos == null) return null;
        if (!com.sqc.nuclearsq.data.ChunkGuard.loaded(level, controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof TurbineControllerBlockEntity c && c.isFormed()) return c;
        return null;
    }

    public void sendInfo(Player player) {
        TurbinePortType t = type();
        String k = "message.nuclearsq.port.";
        player.sendSystemMessage(Component.translatable(k + "title",
                Component.translatable("block.nuclearsq.turbine_port_" + t.id() + "_sqc"),
                Component.translatable(k + (t.input ? "in" : "out"))));
        player.sendSystemMessage(Component.translatable(k + "turbine_" + t.id() + ".connect"));
        TurbineControllerBlockEntity c = findController();
        if (c == null) {
            player.sendSystemMessage(Component.translatable(k + "not_formed"));
            return;
        }
        player.sendSystemMessage(switch (t) {
            case ENERGY -> Component.translatable(k + "turbine_energy.state",
                    c.energyStored(), TurbineControllerBlockEntity.CAP, c.lastFe(), TurbineControllerBlockEntity.OUT);
            case STEAM -> Component.translatable(k + "turbine_steam.state",
                    c.steam().getFluidAmount(), TurbineControllerBlockEntity.TANK, c.maxMb());
            case WATER -> Component.translatable(k + "turbine_water.state",
                    c.condensateOut().getFluidInTank(0).getAmount(), TurbineControllerBlockEntity.WATER_TANK,
                    TurbineControllerBlockEntity.WATER_OUT);
        });
        player.sendSystemMessage(Component.translatable(k + "formed", c.rpm(), TurbineControllerBlockEntity.MAX_RPM));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("controller", controllerPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
    }
}
