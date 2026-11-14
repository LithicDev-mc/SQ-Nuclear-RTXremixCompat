package com.sqc.nuclearsq.module;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ModuleBlockEntity extends BlockEntity {
    private static final int STALE = 45;

    private long seen = -1000;
    @Nullable
    private Component machine;
    private boolean active;
    @Nullable
    private String reason;

    public ModuleBlockEntity(BlockPos pos, BlockState st) {
        super(ModuleRegistry.MODULE_BE.get(), pos, st);
    }

    public ModuleKind kind() {
        return getBlockState().getBlock() instanceof ModuleBlock b ? b.kind : ModuleKind.FAN;
    }

    public void report(Component machineName, boolean active, @Nullable String reason) {
        if (level == null) return;
        seen = level.getGameTime();
        this.machine = machineName;
        this.active = active;
        this.reason = reason;
        setState(active ? ModuleState.ACTIVE : ModuleState.WARN);
    }

    void serverTick() {
        if (level == null || level.getGameTime() % 20 != 7) return;
        if (level.getGameTime() - seen > STALE) {
            machine = null;
            setState(ModuleState.IDLE);
        }
    }

    private void setState(ModuleState s) {
        BlockState st = getBlockState();
        if (level != null && st.hasProperty(ModuleBlock.STATE) && st.getValue(ModuleBlock.STATE) != s) {
            level.setBlock(worldPosition, st.setValue(ModuleBlock.STATE, s), Block.UPDATE_ALL);
        }
    }

    public boolean active() {
        return machine != null && active;
    }

    public Component statusLine() {
        String k = "message.nuclearsq.module.";
        if (machine == null) return Component.translatable(k + "none").withStyle(ChatFormatting.GRAY);
        if (active) return Component.translatable(k + "active." + kind().name().toLowerCase(java.util.Locale.ROOT), machine).withStyle(ChatFormatting.GREEN);
        return Component.translatable(k + "warn." + (reason == null ? "max" : reason), machine).withStyle(ChatFormatting.GOLD);
    }
}
