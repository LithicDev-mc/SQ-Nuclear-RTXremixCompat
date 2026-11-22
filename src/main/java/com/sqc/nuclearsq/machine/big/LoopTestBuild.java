package com.sqc.nuclearsq.machine.big;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sqc.nuclearsq.kit.MachineKits;
import com.sqc.nuclearsq.reactor.FuelRodHeadBlock;
import com.sqc.nuclearsq.reactor.ReactorProps;
import com.sqc.nuclearsq.registry.ReactorRegistry;
import com.sqc.nuclearsq.transport.BigPipeBlock;
import com.sqc.nuclearsq.transport.ConduitBlock;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.TransportRegistry;
import com.sqc.nuclearsq.transport.TransportTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class LoopTestBuild {
    private LoopTestBuild() {}

    public static final List<String> NAMES = List.of("primary", "extended", "full");

    public record Result(String message, Map<String, BlockPos> anchors, BlockPos lo, BlockPos hi) {}

    private static JsonObject read(String name) {
        String res = "/data/nuclearsq/testbuilds/" + name + ".json";
        try (InputStream in = LoopTestBuild.class.getResourceAsStream(res)) {
            if (in == null) throw new IllegalStateException(res + " fehlt");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(res + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static BlockPos at(BlockPos origin, JsonArray a) {
        return origin.offset(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    public static BlockPos[] bounds(String name, BlockPos origin) {
        JsonObject plan = read(name);
        return new BlockPos[] {at(origin, plan.getAsJsonArray("lo")), at(origin, plan.getAsJsonArray("hi"))};
    }

    public static BlockPos originFor(String name, BlockPos player, Direction look) {
        JsonObject plan = read(name);
        JsonArray lo = plan.getAsJsonArray("lo"), hi = plan.getAsJsonArray("hi");
        int lx = lo.get(0).getAsInt(), lz = lo.get(2).getAsInt(), hx = hi.get(0).getAsInt(), hz = hi.get(2).getAsInt();
        int gap = 8;
        int x = switch (look) {
            case EAST -> player.getX() + gap - lx;
            case WEST -> player.getX() - gap - hx;
            default -> player.getX() - (lx + hx) / 2;
        };
        int z = switch (look) {
            case SOUTH -> player.getZ() + gap - lz;
            case NORTH -> player.getZ() - gap - hz;
            case EAST, WEST -> player.getZ() - (lz + hz) / 2;
            default -> player.getZ() + gap - lz;
        };
        return new BlockPos(x, player.getY() - lo.get(1).getAsInt(), z);
    }

    public static void clear(ServerLevel lv, BlockPos lo, BlockPos hi) {
        for (BlockPos p : BlockPos.betweenClosed(lo.offset(-2, 0, -2), hi.offset(2, 16, 2))) {
            if (!lv.getBlockState(p).isAir()) lv.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void remove(ServerLevel lv, BlockPos origin, String name, Result r) {
        JsonObject plan = read(name);
        clear(lv, r.lo(), r.hi());
        for (JsonElement e : plan.getAsJsonArray("dig")) {
            JsonArray box = e.getAsJsonArray();
            for (BlockPos p : BlockPos.betweenClosed(at(origin, box.get(0).getAsJsonArray()), at(origin, box.get(1).getAsJsonArray()))) {
                lv.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (JsonElement e : plan.getAsJsonArray("stone")) lv.setBlock(at(origin, e.getAsJsonArray()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public static Result build(ServerLevel lv, BlockPos origin, String name) {
        JsonObject plan = read(name);
        BlockPos lo = at(origin, plan.getAsJsonArray("lo")), hi = at(origin, plan.getAsJsonArray("hi"));
        clear(lv, lo, hi);
        for (JsonElement e : plan.getAsJsonArray("dig")) {
            JsonArray box = e.getAsJsonArray();
            for (BlockPos p : BlockPos.betweenClosed(at(origin, box.get(0).getAsJsonArray()), at(origin, box.get(1).getAsJsonArray()))) {
                lv.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (JsonElement e : plan.getAsJsonArray("stone")) lv.setBlock(at(origin, e.getAsJsonArray()), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (JsonElement e : plan.getAsJsonArray("water")) lv.setBlock(at(origin, e.getAsJsonArray()), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        Map<String, BlockPos> anchors = new LinkedHashMap<>();
        for (JsonElement e : plan.getAsJsonArray("machines")) {
            JsonObject m = e.getAsJsonObject();
            String id = m.get("id").getAsString();
            MachineKits.Def def = MachineKits.def(id);
            if (def == null) throw new IllegalStateException("Testaufbau " + name + ": Anlage " + id + " fehlt");
            BlockPos anchor = at(origin, m.getAsJsonArray("anchor"));
            MachineKits.place(lv, new MachineKits.Placement(def, anchor, m.get("rot").getAsInt()));
            anchors.put(m.get("key").getAsString(), anchor);
        }
        for (JsonElement e : plan.getAsJsonArray("rods")) rods(lv, anchors.get(e.getAsString()));
        for (JsonElement e : plan.getAsJsonArray("charge")) {
            JsonArray c = e.getAsJsonArray();
            if (lv.getBlockEntity(anchors.get(c.get(0).getAsString())) instanceof BigMachineBlockEntity be && be.energy() != null) {
                be.energy().setStored(Math.min(be.energy().getMaxEnergyStored(), c.get(1).getAsInt()));
            }
        }
        for (JsonElement e : plan.getAsJsonArray("fills")) {
            JsonArray f = e.getAsJsonArray();
            if (!(lv.getBlockEntity(anchors.get(f.get(0).getAsString())) instanceof BigMachineBlockEntity be)) continue;
            var tank = be.tank(f.get(1).getAsString());
            if (tank == null) continue;
            tank.fill(new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse(f.get(2).getAsString())), f.get(3).getAsInt()),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        Direction.Axis[] axes = Direction.Axis.values();
        for (JsonElement e : plan.getAsJsonArray("bigpipes")) {
            JsonArray c = e.getAsJsonArray();
            lv.setBlockAndUpdate(at(origin, c), TransportRegistry.BIG_PIPE.get().defaultBlockState()
                    .setValue(BigPipeBlock.AXIS, axes[c.get(3).getAsInt()]).setValue(BigPipeBlock.QUAD, c.get(4).getAsInt()));
        }
        List<BlockPos> laid = new ArrayList<>();
        TransportTier[] tiers = TransportTier.values();
        for (String kind : new String[] {"pipes", "cables"}) {
            var blocks = kind.equals("pipes") ? TransportRegistry.PIPES : TransportRegistry.CABLES;
            for (JsonElement e : plan.getAsJsonArray(kind)) {
                JsonArray c = e.getAsJsonArray();
                BlockPos p = at(origin, c);
                lv.setBlockAndUpdate(p, blocks.get(tiers[c.get(3).getAsInt()]).get().defaultBlockState());
                laid.add(p);
            }
        }
        Direction[] sides = Direction.values();
        for (JsonElement e : plan.getAsJsonArray("off")) {
            JsonArray c = e.getAsJsonArray();
            if (!(lv.getBlockEntity(at(origin, c)) instanceof ConduitBlockEntity be)) continue;
            Direction side = sides[c.get(3).getAsInt()];
            while (be.mode(side) != ConduitBlockEntity.OFF) be.cycle(side, true);
        }
        for (BlockPos p : laid) {
            if (lv.getBlockState(p).getBlock() instanceof ConduitBlock c) c.refresh(lv, p);
        }
        StringBuilder msg = new StringBuilder("Test build " + name + " placed.");
        for (var a : anchors.entrySet()) msg.append(' ').append(a.getKey()).append(' ').append(a.getValue().toShortString()).append(';');
        msg.append(" Rod target is 0.");
        return new Result(msg.toString(), anchors, lo, hi);
    }

    private static void rods(ServerLevel lv, BlockPos drive) {
        if (!(lv.getBlockEntity(drive) instanceof BigMachineBlockEntity be)) return;
        int head = RodShaft.FULL_HEAD_Y + BigMachine.CONTAINMENT.h - BigMachineBlockEntity.CONTAINMENT_DRIVE_Y - BigMachine.CONTROL_ROD_DRIVE.h + 1
                + FuelRodHeadBlock.ROD_BLOCKS;
        BlockState rod = ReactorRegistry.FUEL_ROD_BLOCK.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true);
        BlockState top = ReactorRegistry.FUEL_ROD_HEAD.get().defaultBlockState().setValue(ReactorProps.ROD_FORMED, true);
        for (RodShaft s : be.shafts()) {
            BlockPos h = be.rodBase(s).above(head);
            for (int i = 1; i <= FuelRodHeadBlock.ROD_BLOCKS; i++) lv.setBlockAndUpdate(h.below(i), rod);
            lv.setBlockAndUpdate(h, top);
        }
    }
}
