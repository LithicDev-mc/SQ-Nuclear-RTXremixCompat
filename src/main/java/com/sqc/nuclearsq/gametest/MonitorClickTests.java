package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.MonitorTerminal;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.multiblock.MultiblockStructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(NuclearSQ.MODID)
public final class MonitorClickTests {
    private MonitorClickTests() {}

    @GameTestGenerator
    public static Collection<TestFunction> monitorClicks() {
        List<TestFunction> out = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (BigMachine m : BigMachine.values()) if (m.monitor != null) ids.add(m.id);
        ids.add("turbine");
        for (String id : ids) {
            for (int rot = 0; rot < 4; rot++) {
                int r = rot;
                String name = "monitorclick_" + id + "_rot" + rot;
                out.add(new TestFunction(name, "nuclearsq:" + name, "nuclearsq:floor", Rotation.NONE, 100, 0, true, false, 1, 1,
                        false, h -> click(h, id, r)));
            }
        }
        return out;
    }

    private static MultiblockStructure structure(String id) {
        if (id.equals("turbine")) return MultiblockStructure.TURBINE;
        for (BigMachine m : BigMachine.values()) if (m.id.equals(id)) return m.structure;
        throw new IllegalArgumentException(id);
    }

    private static void click(GameTestHelper h, String id, int rot) {
        MultiblockStructure s = structure(id);
        BlockPos ctrl = h.absolutePos(new BlockPos(7, 4, 7));
        List<BlockPos> placed = new ArrayList<>();
        for (MultiblockStructure.Cell c : s.cells(ctrl, rot)) {
            h.getLevel().setBlockAndUpdate(c.pos(), c.block().defaultBlockState());
            placed.add(c.pos());
        }
        int[] n = MultiblockStructure.rotate(0, -1, rot);
        h.runAfterDelay(25, () -> {
            try {
                if (!(h.getLevel().getBlockEntity(ctrl) instanceof MonitorTerminal mt)) throw new GameTestAssertException(id + ": kein Controller");
                if (mt.monitorRot() != rot) throw new GameTestAssertException(id + " Drehung " + rot + ": nicht aufgebaut (" + mt.monitorRot() + ")");
                AABB box = mt.monitorWorldBox(ctrl);
                Vec3 screen = box.getCenter();
                Vec3 eye = screen.add(n[0] * 2.5, 0, n[1] * 2.5);
                net.minecraft.server.level.ServerPlayer sp = new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"),
                        net.minecraft.server.level.ClientInformation.createDefault()) {
                    @Override
                    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,
                                                          java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra) {
                        containerMenu = provider.createMenu(1, getInventory(), this);
                        return java.util.OptionalInt.of(1);
                    }
                };
                sp.setPos(eye.x, eye.y - sp.getEyeHeight(), eye.z);
                Vec3 far = screen.add(screen.subtract(eye).normalize().scale(2));
                var hit = h.getLevel().clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, sp));
                if (hit.getType() == HitResult.Type.MISS) throw new GameTestAssertException(id + " Drehung " + rot + ": Blick auf den Monitor trifft keinen Block");
                if (com.sqc.nuclearsq.machine.MonitorClick.find(h.getLevel(), hit.getBlockPos(), eye, hit.getLocation()) != mt) {
                    throw new GameTestAssertException(id + " Drehung " + rot + ": Monitor-Flaeche wird beim Klick auf "
                            + h.getLevel().getBlockState(hit.getBlockPos()) + " nicht erkannt");
                }
                sp.gameMode.useItemOn(sp, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
                if (sp.containerMenu == sp.inventoryMenu) {
                    throw new GameTestAssertException(id + " Drehung " + rot + ": Klick auf " + h.getLevel().getBlockState(hit.getBlockPos())
                            + " oeffnet kein Terminal");
                }
                h.succeed();
            } finally {
                h.getLevel().setBlock(ctrl, Blocks.AIR.defaultBlockState(), 3);
                for (BlockPos p : placed) h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                BlockPos.betweenClosedStream(AABB.encapsulatingFullBlocks(placed.get(0), placed.get(placed.size() - 1)).inflate(16))
                        .forEach(p -> {
                            var st = h.getLevel().getBlockState(p);
                            if (st.getBlock() instanceof com.sqc.nuclearsq.machine.big.BigShellBlock
                                    || st.getBlock() instanceof com.sqc.nuclearsq.turbine.TurbineShellBlock) {
                                h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                            }
                        });
            }
        });
    }
}
