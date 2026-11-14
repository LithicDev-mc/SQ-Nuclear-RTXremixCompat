package com.sqc.nuclearsq.gametest;

import java.util.concurrent.atomic.AtomicInteger;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.compat.cc.DataCcCompat;
import com.sqc.nuclearsq.monitor.ImageMonitorBlock;
import com.sqc.nuclearsq.monitor.ImageMonitorBlockEntity;
import com.sqc.nuclearsq.monitor.ImageMonitorInputPayload;
import com.sqc.nuclearsq.monitor.ImageMonitorPayload;
import com.sqc.nuclearsq.monitor.ImageMonitorRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class ImageMonitorTests {
    private ImageMonitorTests() {}

    private static final String FLOOR = "floor";

    private static BlockState state(Direction facing, AttachFace face) {
        return ImageMonitorRegistry.MONITOR.get().defaultBlockState()
                .setValue(ImageMonitorBlock.FACING, facing)
                .setValue(ImageMonitorBlock.FACE, face);
    }

    private static ImageMonitorBlockEntity place(GameTestHelper h, BlockPos rel, Direction facing) {
        h.setBlock(rel, state(facing, AttachFace.WALL));
        if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof ImageMonitorBlockEntity be)) {
            throw new GameTestAssertException("Kein Bildmonitor bei " + rel);
        }
        return be;
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 60)
    public static void screenIsFullRectangle(GameTestHelper h) {
        Direction f = Direction.NORTH;
        BlockPos base = new BlockPos(3, 4, 3);
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
            place(h, base.relative(Direction.WEST, i).above(j), f);
        }
        ImageMonitorBlock.Screen s = ImageMonitorBlock.screen(h.getLevel(), h.absolutePos(base.above()));
        check(s != null && s.width() == 2 && s.height() == 2, "Bildschirm sollte 2x2 sein: " + s);
        check(s.origin().equals(h.absolutePos(base.relative(Direction.WEST, 0))), "Ursprung unten links erwartet: " + s.origin());
        h.setBlock(base.above(), Blocks.AIR);
        ImageMonitorBlock.Screen t = ImageMonitorBlock.screen(h.getLevel(), h.absolutePos(base));
        check(t != null && t.width() == 1 && t.height() == 1, "Mit Loch sollte jeder Monitor allein stehen: " + t);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 60)
    public static void resolutionAndPixels(GameTestHelper h) {
        ImageMonitorBlockEntity be = place(h, new BlockPos(3, 4, 3), Direction.NORTH);
        place(h, new BlockPos(2, 4, 3), Direction.NORTH);
        ImageMonitorBlock.Screen s = ImageMonitorBlock.screen(h.getLevel(), be.getBlockPos());
        check(s != null && s.width() == 2, "Bildschirm 2x1 erwartet");
        ImageMonitorBlockEntity main = (ImageMonitorBlockEntity) h.getLevel().getBlockEntity(s.origin());
        main.ensure(s);
        int[] res = main.resolution(s);
        check(res[0] == 2 * ImageMonitorBlockEntity.PIXELS_PER_BLOCK && res[1] == ImageMonitorBlockEntity.PIXELS_PER_BLOCK,
                "Aufloesung 128x64 erwartet, war " + res[0] + "x" + res[1]);
        main.clear(0x000000);
        main.put(3, 4, 0x112233);
        check(main.getPixel(3, 4) == 0x112233, "Pixel gelesen: " + Integer.toHexString(main.getPixel(3, 4)));
        main.fill(10, 10, 4, 4, 0xFF0000);
        check(main.getPixel(13, 13) == 0xFF0000 && main.getPixel(14, 14) == 0, "Fuellrechteck passt nicht");
        main.put(9999, 9999, 0xFFFFFF);
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 60)
    public static void touchMapsToPixel(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 4, 3);
        ImageMonitorBlockEntity be = place(h, rel, Direction.NORTH);
        BlockPos abs = h.absolutePos(rel);
        ImageMonitorBlock.Screen s = ImageMonitorBlock.screen(h.getLevel(), abs);
        be.ensure(s);
        be.setPpb(64);
        be.adapt(s);
        AtomicInteger px = new AtomicInteger(-1), py = new AtomicInteger(-1);
        be.touchListeners.put(px, (player, x, y) -> { px.set(x); py.set(y); });
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        Vec3 hitAt = Vec3.atLowerCornerOf(abs).add(0.25, 0.75, 0.5);
        BlockHitResult hit = new BlockHitResult(hitAt, Direction.NORTH, abs, false);
        h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), sp, hit);
        check(px.get() == 49 && py.get() == 17, "Beruehrung erwartet 49/17, war " + px.get() + "/" + py.get());
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 80)
    public static void idleImageAppears(GameTestHelper h) {
        ImageMonitorBlockEntity be = place(h, new BlockPos(3, 4, 3), Direction.NORTH);
        h.runAfterDelay(25, () -> {
            check(be.pixels() != null, "Kein Startbild nach 25 Ticks");
            check(be.getPixel(0, 0) == 0x00B4E6, "Rahmenfarbe erwartet, war " + Integer.toHexString(be.getPixel(0, 0)));
            be.clear(0x000000);
            check(be.getPixel(0, 0) == 0, "Bild sollte schwarz sein");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 60)
    public static void controlInputReachesComputer(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 4, 3);
        ImageMonitorBlockEntity be = place(h, rel, Direction.NORTH);
        BlockPos abs = h.absolutePos(rel);
        ImageMonitorBlock.Screen s = ImageMonitorBlock.screen(h.getLevel(), abs);
        be.ensure(s);
        java.util.List<String> events = new java.util.ArrayList<>();
        java.util.List<Object[]> values = new java.util.ArrayList<>();
        be.touchListeners.put(events, new ImageMonitorBlockEntity.TouchListener() {
            @Override
            public void touch(String player, int x, int y) {}

            @Override
            public void input(String kind, Object... args) {
                events.add(kind);
                values.add(args);
            }
        });
        ServerPlayer sp = AuditFixTests.testPlayer(h);
        ImageMonitorInputPayload.apply(sp, new ImageMonitorInputPayload(abs, ImageMonitorInputPayload.TOUCH, 9999, 0, 1, ""));
        check(events.equals(java.util.List.of("touch")), "touch erwartet: " + events);
        int w = be.resWidth();
        check(((Integer) values.get(0)[0]) == w && ((Integer) values.get(0)[1]) == 1, "Pixel auf den Bildschirm begrenzt");
        ImageMonitorInputPayload.apply(sp, new ImageMonitorInputPayload(abs, ImageMonitorInputPayload.KEY, 0, 0, 65 << 1 | 1, ""));
        check(events.get(1).equals("key") && ((Integer) values.get(1)[0]) == 65 && Boolean.TRUE.equals(values.get(1)[1]), "key 65 gehalten erwartet");
        ImageMonitorInputPayload.apply(sp, new ImageMonitorInputPayload(abs, ImageMonitorInputPayload.CHAR, 0, 0, 0, "x"));
        check(events.get(2).equals("char") && "x".equals(values.get(2)[0]), "char x erwartet");
        int before = be.ppb();
        ImageMonitorInputPayload.apply(sp, new ImageMonitorInputPayload(abs, ImageMonitorInputPayload.CYCLE, 0, 0, 0, ""));
        check(be.ppb() != before, "Aufloesung sollte weiterschalten");
        sp.setPos(sp.getX() + 40, sp.getY(), sp.getZ());
        int n = events.size();
        ImageMonitorInputPayload.apply(sp, new ImageMonitorInputPayload(abs, ImageMonitorInputPayload.MOVE, 1, 1, 0, ""));
        check(events.size() == n, "Weit weg darf nichts ankommen");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 60)
    public static void smallTextIsCrispBitmap(GameTestHelper h) {
        ImageMonitorBlockEntity be = place(h, new BlockPos(3, 4, 3), Direction.NORTH);
        ImageMonitorBlock.Screen s = ImageMonitorBlock.screen(h.getLevel(), be.getBlockPos());
        be.ensure(s);
        be.clear(0);
        if (ModList.get().isLoaded("computercraft")) {
            var p = (com.sqc.nuclearsq.compat.cc.ImageMonitorPeripheral) h.getLevel()
                    .getCapability(dan200.computercraft.api.peripheral.PeripheralCapability.get(), be.getBlockPos(), Direction.EAST);
            try {
                Object[] wh = p.measureText("Hello", java.util.Optional.of(8));
                check(((Number) wh[0]).intValue() == 25 && ((Number) wh[1]).intValue() == 8, "5x8 mal 5 Zeichen erwartet, war " + wh[0] + "x" + wh[1]);
            } catch (dan200.computercraft.api.lua.LuaException e) {
                throw new GameTestAssertException("measureText scheiterte: " + e.getMessage());
            }
        }
        var face = com.sqc.nuclearsq.compat.cc.MonitorBitmapFont.forSize(8);
        check(face != null && face.width() == 5 && face.height() == 8, "Schrift fuer Groesse 8 erwartet");
        int on = 0;
        for (int y = 0; y < face.height(); y++) for (int x = 0; x < face.width(); x++) if (face.on('H', x, y)) on++;
        check(on > 8 && on < 30, "H hat sichtbare Pixel: " + on);
        check(com.sqc.nuclearsq.compat.cc.MonitorBitmapFont.forSize(16) == null, "ab 16 weiter Vektorschrift");
        check(!com.sqc.nuclearsq.compat.cc.MonitorBitmapFont.supports("Größe"), "Umlaute nicht in der Pixelschrift");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 60)
    public static void payloadRoundTrip(GameTestHelper h) {
        byte[] data = {1, 2, 3, 4, 5, 6};
        ImageMonitorPayload in = new ImageMonitorPayload(new BlockPos(1, 2, 3), 2, 1, 0, data);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        ImageMonitorPayload.STREAM_CODEC.encode(buf, in);
        ImageMonitorPayload out = ImageMonitorPayload.STREAM_CODEC.decode(buf);
        check(out.pos().equals(in.pos()) && out.width() == 2 && out.height() == 1 && out.y() == 0
                && java.util.Arrays.equals(out.data(), data), "Paket kam nicht gleich zurueck");
        ImageMonitorInputPayload inp = new ImageMonitorInputPayload(new BlockPos(1, 2, 3), 7, 4, 5, 6, "a");
        RegistryFriendlyByteBuf buf2 = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        ImageMonitorInputPayload.STREAM_CODEC.encode(buf2, inp);
        check(ImageMonitorInputPayload.STREAM_CODEC.decode(buf2).equals(inp), "Eingabe-Paket kam nicht gleich zurueck");
        check(ImageMonitorPayload.TYPE.id().getNamespace().equals(NuclearSQ.MODID), "Kanal im eigenen Namensraum erwartet");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 60)
    public static void peripheralTypeIfCc(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 4, 3);
        place(h, rel, Direction.NORTH);
        if (ModList.get().isLoaded("computercraft")) {
            String type = DataCcCompat.peripheralType(h.getLevel(), h.absolutePos(rel));
            check("sqc_image_monitor".equals(type), "Peripherietyp sqc_image_monitor erwartet, war " + type);
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "image_monitor", timeoutTicks = 60)
    public static void computerDrawsAndGetsTouch(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 4, 3);
        ImageMonitorBlockEntity be = place(h, rel, Direction.NORTH);
        BlockPos abs = h.absolutePos(rel);
        if (ModList.get().isLoaded("computercraft")) CcPart.run(h, be, abs);
        h.succeed();
    }

    private static final class CcPart {
        static void run(GameTestHelper h, ImageMonitorBlockEntity be, BlockPos abs) {
            var peripheral = h.getLevel().getCapability(dan200.computercraft.api.peripheral.PeripheralCapability.get(), abs, Direction.EAST);
            check(peripheral != null, "Kein Peripheral an der Seite EAST");
            java.util.List<String> events = new java.util.ArrayList<>();
            var access = (dan200.computercraft.api.peripheral.IComputerAccess) java.lang.reflect.Proxy.newProxyInstance(
                    ImageMonitorTests.class.getClassLoader(), new Class<?>[]{dan200.computercraft.api.peripheral.IComputerAccess.class},
                    (proxy, m, a) -> {
                        if (m.getName().equals("queueEvent")) events.add((String) a[0]);
                        if (m.getName().equals("getAttachmentName")) return "monitor_0";
                        Class<?> r = m.getReturnType();
                        if (r == boolean.class) return Boolean.FALSE;
                        if (r == int.class) return 0;
                        if (r == long.class) return 0L;
                        return null;
                    });
            peripheral.attach(access);
            var p = (com.sqc.nuclearsq.compat.cc.ImageMonitorPeripheral) peripheral;
            try {
                p.setResolution(64, 64);
                Object[] res = p.getResolution();
                check(((Number) res[0]).intValue() == 64, "Aufloesung 64 erwartet");
            } catch (dan200.computercraft.api.lua.LuaException e) {
                throw new GameTestAssertException("Peripheral-Aufruf scheiterte: " + e.getMessage());
            }
            ServerPlayer sp = AuditFixTests.testPlayer(h);
            Vec3 at = Vec3.atLowerCornerOf(abs).add(0.5, 0.5, 0.5);
            h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), sp, new BlockHitResult(at, Direction.NORTH, abs, false));
            check(events.contains("sqc_touch"), "Computer bekam kein sqc_touch: " + events);
            peripheral.detach(access);
        }
    }
}
