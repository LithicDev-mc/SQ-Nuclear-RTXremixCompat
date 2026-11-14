package com.sqc.nuclearsq.compat.jade;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigMachineControllerBlock;
import com.sqc.nuclearsq.machine.big.BigPortBlock;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;
import com.sqc.nuclearsq.machine.big.BigShellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public class NsqJadePlugin implements IWailaPlugin, IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "machine");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void register(IWailaCommonRegistration r) {
        r.registerBlockDataProvider(this, BigMachineBlockEntity.class);
        r.registerBlockDataProvider(this, BigPortBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration r) {
        r.registerBlockComponent(this, BigMachineControllerBlock.class);
        r.registerBlockComponent(this, BigPortBlock.class);
        r.addRayTraceCallback((hit, acc, orig) -> {
            if (!(acc instanceof BlockAccessor b) || !(b.getBlock() instanceof BigShellBlock s)) return acc;
            BlockPos c = controller(b.getLevel(), b.getPosition(), b.getBlockState(), s.machine());
            return c == null ? acc : r.blockAccessor().from(b).blockState(b.getLevel().getBlockState(c))
                    .blockEntity(b.getLevel().getBlockEntity(c)).hit(b.getHitResult().withPosition(c)).build();
        });
    }

    @Nullable
    private static BlockPos controller(Level level, BlockPos pos, BlockState st, BigMachine m) {
        int rot = st.getValue(BigShellBlock.ROT);
        int idx = st.getValue((IntegerProperty) st.getBlock().getStateDefinition().getProperty("shape"));
        for (int[] c : m.cells) {
            if (c[3] != idx) continue;
            BlockPos p = pos.subtract(m.structure.world(BlockPos.ZERO, rot, c[0], c[1], c[2]));
            if (level.getBlockEntity(p) instanceof BigMachineBlockEntity be && be.machine() == m && be.formedRot() == rot) return p;
        }
        return null;
    }

    @Override
    public void appendServerData(CompoundTag t, BlockAccessor a) {
        boolean port = a.getBlockEntity() instanceof BigPortBlockEntity;
        BigMachineBlockEntity c = a.getBlockEntity() instanceof BigPortBlockEntity p ? p.findController()
                : a.getBlockEntity() instanceof BigMachineBlockEntity b ? b : null;
        if (c == null || !c.isFormed()) return;
        t.putString("state", c.stateKey());
        MachineEnergy e = c.energy();
        if (e != null && !c.isGenerator() && !port) {
            t.putInt("fe", e.getEnergyStored());
            t.putInt("max", e.getMaxEnergyStored());
        }
        if (c.isGenerator() || c.lastOut() > 0) t.putInt("out", c.lastOut());
        BigMachine.Energy spec = c.machine().energy;
        if (c.isRunning() && e != null && spec != null && spec.use() > 0) t.putInt("use", spec.use());
    }

    @Override
    public void appendTooltip(ITooltip tip, BlockAccessor a, IPluginConfig config) {
        CompoundTag t = a.getServerData();
        if (!t.contains("state")) return;
        tip.add(Component.translatable("message.nuclearsq.big.state." + t.getString("state")));
        if (t.contains("max")) tip.add(Component.translatable("jade.nuclearsq.energy", n(t.getInt("fe")), n(t.getInt("max"))));
        if (t.contains("out")) tip.add(Component.translatable("jade.nuclearsq.output", n(t.getInt("out"))));
        if (t.contains("use")) tip.add(Component.translatable("jade.nuclearsq.use", n(t.getInt("use"))));
    }

    private static String n(int v) {
        return String.format("%,d", v);
    }
}
