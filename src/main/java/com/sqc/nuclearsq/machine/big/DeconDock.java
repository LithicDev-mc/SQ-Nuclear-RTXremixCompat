package com.sqc.nuclearsq.machine.big;

import com.sqc.nuclearsq.machine.MachineEnergy;
import com.sqc.nuclearsq.radiation.ItemContamination;
import com.sqc.nuclearsq.robotics.decon.DeconRegistry;
import com.sqc.nuclearsq.robotics.decon.DeconRobotEntity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public class DeconDock {
    private final BigMachineBlockEntity be;
    private final MachineEnergy energy;
    @Nullable private final FluidTank lye, diesel;

    public DeconDock(BigMachineBlockEntity be, MachineEnergy energy, @Nullable FluidTank lye, @Nullable FluidTank diesel) {
        this.be = be;
        this.energy = energy;
        this.lye = lye;
        this.diesel = diesel;
    }

    public static boolean handles(BigMachine m) {
        return m == BigMachine.DECON_DOCK;
    }

    public int tick(Level level, int rot, boolean enabled) {
        if (rot < 0 || !be.isFormed() || !enabled) return 0;
        BigMachine m = be.machine();
        BlockPos pos = be.getBlockPos();
        BlockPos front = m.structure.world(pos, rot, 1, 0, 0), back = m.structure.world(pos, rot, 1, 0, 1), far = m.structure.world(pos, rot, 2, 1, 1);
        AABB bay = new AABB(Math.min(front.getX(), far.getX()), Math.min(front.getY(), far.getY()), Math.min(front.getZ(), far.getZ()),
                Math.max(front.getX(), far.getX()) + 1, Math.max(front.getY(), far.getY()) + 1, Math.max(front.getZ(), far.getZ()) + 1);
        int moved = 0;
        for (DeconRobotEntity r : com.sqc.nuclearsq.compat.sable.SableCompat.entitiesIn(level, DeconRobotEntity.class, bay, e -> e.isAlive() && (com.sqc.nuclearsq.compat.sable.SableCompat.isOnSubLevel(level, pos) || bay.contains(e.position())))) {
            Vec3 dir = Vec3.directionFromRotation(0, r.getYRot());
            if (dir.x * (back.getX() - front.getX()) + dir.z * (back.getZ() - front.getZ()) > -0.7) continue;
            moved += dock(r);
        }
        return moved;
    }

    private int dock(DeconRobotEntity r) {
        int fe = Math.min(Math.min(Math.max(1, be.machine().extra("charge_fe_t")), DeconRobotEntity.BATTERY - r.energy()), energy.getEnergyStored());
        if (fe > 0) {
            r.setEnergy(r.energy() + fe);
            energy.drain(fe);
        }
        int per = Math.max(1, be.machine().extra("diesel_fe_mb")), room = DeconRobotEntity.BATTERY - r.energy();
        if (diesel != null && fe < be.machine().extra("charge_fe_t") && room >= per) {
            int d = diesel.drain(Math.min(Math.max(1, be.machine().extra("diesel_mb_t")), room / per), IFluidHandler.FluidAction.EXECUTE).getAmount();
            r.setEnergy(r.energy() + d * per);
            fe += d * per;
        }
        int mb = 0;
        if (lye != null) {
            int want = Math.min(Math.max(1, be.machine().extra("lye_mb_t")), DeconRobotEntity.AGENT_MAX - r.agent());
            FluidStack got = want > 0 ? lye.drain(want, IFluidHandler.FluidAction.EXECUTE) : FluidStack.EMPTY;
            mb = got.getAmount();
            if (mb > 0) r.setAgent(r.agent() + mb);
        }
        int rubble = 0;
        ItemStackHandler out = be.items();
        if (out != null && r.waste() > 0) {
            ItemStack st = new ItemStack(DeconRegistry.RUBBLE.get());
            ItemContamination.set(st, DeconRobotEntity.RUBBLE_SV_H);
            if (ItemHandlerHelper.insertItem(out, st, true).isEmpty()) {
                ItemHandlerHelper.insertItem(out, st, false);
                rubble = Math.min(r.waste(), DeconRobotEntity.RUBBLE_PER_ITEM);
                r.setWaste(r.waste() - rubble);
            }
        }
        if (fe > 0 || mb > 0 || rubble > 0) be.setChanged();
        return Math.max(fe, 0) + mb + rubble;
    }
}
