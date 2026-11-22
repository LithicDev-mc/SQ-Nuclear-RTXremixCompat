package com.sqc.nuclearsq.compat.dh;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockColorOverrideEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockStateWrapperCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineControllerBlock;
import com.sqc.nuclearsq.machine.big.BigShellBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class DhCompat {
    private DhCompat() {}

    private static final Map<BlockState, Integer> COLORS = new ConcurrentHashMap<>();
    private static final Map<BigMachine, Integer> MACHINE_COLORS = new ConcurrentHashMap<>();

    public static void register() {
        try {
            DhApi.events.bind(DhApiBlockStateWrapperCreatedEvent.class, new DhApiBlockStateWrapperCreatedEvent() {
                @Override
                public void blockStateWrapperCreated(DhApiEventParam<EventParam> event) {
                    if (!(event.value.getBlockStateWrapper().getWrappedMcObject() instanceof BlockState state) || !hidden(state)) return;
                    COLORS.put(state, color(state));
                    event.value.setOpacity(16);
                    event.value.setBlockMaterial(EDhApiBlockMaterial.METAL);
                    event.value.setAllowApiColorOverride(true);
                }
            });
            DhApi.events.bind(DhApiBlockColorOverrideEvent.class, new DhApiBlockColorOverrideEvent() {
                @Override
                public void onBlockColorOverridden(DhApiEventParam<EventParam> event) {
                    if (!(event.value.getBlockStateWrapper().getWrappedMcObject() instanceof BlockState state)) return;
                    Integer c = COLORS.get(state);
                    if (c != null) event.value.setColor(255, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
                }
            });
        } catch (RuntimeException | LinkageError e) {
            NuclearSQ.LOGGER.warn("Distant Horizons gefunden, aber die API passt nicht; Fernsicht der Anlagen bleibt aus", e);
        }
    }

    private static boolean hidden(BlockState state) {
        Block block = state.getBlock();
        if (!NuclearSQ.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) return false;
        return block instanceof BigShellBlock || block instanceof BigMachineControllerBlock || state.getRenderShape() == RenderShape.INVISIBLE;
    }

    private static int color(BlockState state) {
        BigMachine machine = state.getBlock() instanceof BigShellBlock s ? s.machine()
                : state.getBlock() instanceof BigMachineControllerBlock c ? c.machine() : null;
        int fallback = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
        if (fallback == 0) fallback = 0x8A8A8A;
        if (machine == null) return fallback;
        final int fb = fallback;
        return MACHINE_COLORS.computeIfAbsent(machine, m -> average(m, fb));
    }

    private static int average(BigMachine machine, int fallback) {
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "textures/block/" + machine.id + ".png");
        var res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return fallback;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int p = img.getPixelRGBA(x, y);
                    if ((p >>> 24) < 128) continue;
                    r += p & 0xFF;
                    g += (p >> 8) & 0xFF;
                    b += (p >> 16) & 0xFF;
                    n++;
                }
            }
            if (n == 0) return fallback;
            return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        } catch (Exception e) {
            return fallback;
        }
    }
}
