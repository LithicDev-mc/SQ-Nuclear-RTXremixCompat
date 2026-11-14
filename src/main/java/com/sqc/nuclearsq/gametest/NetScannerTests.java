package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.DataNet;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.NetScanPayload;
import com.sqc.nuclearsq.data.NetScannerItem;
import com.sqc.nuclearsq.registry.CatalogRegistry;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class NetScannerTests {
    private NetScannerTests() {}

    private static final String FLOOR = "floor";

    private static void build(GameTestHelper h) {
        h.setBlock(new BlockPos(2, 2, 2), DataRegistry.TERMINAL.get().defaultBlockState());
        for (int x = 3; x <= 5; x++) h.setBlock(new BlockPos(x, 2, 2), DataRegistry.CABLE.get().defaultBlockState());
        h.setBlock(new BlockPos(6, 2, 2), DataRegistry.AREA_MONITOR.get().defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(2, 2, 6), DataRegistry.CABLE.get().defaultBlockState());
    }

    @GameTest(template = FLOOR, batch = "netscanner", timeoutTicks = 20)
    public static void traceCollectsWholeNet(GameTestHelper h) {
        build(h);
        DataNet.Trace t = DataNet.trace(h.getLevel(), List.of(h.absolutePos(new BlockPos(4, 2, 2))));
        if (t.cables().size() != 3) throw new GameTestAssertException("Kabel: " + t.cables().size() + " statt 3");
        if (t.cables().contains(h.absolutePos(new BlockPos(2, 2, 6)))) throw new GameTestAssertException("getrenntes Kabel im Netz");
        if (t.sources().size() != 1) throw new GameTestAssertException("Anlagen: " + t.sources().size() + " statt 1");
        if (!t.devices().contains(h.absolutePos(new BlockPos(2, 2, 2)))) throw new GameTestAssertException("Terminal fehlt als Geraet");
        if (!t.ports().containsKey(t.sources().get(0).pos())) throw new GameTestAssertException("Anschlussfeld fehlt");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "netscanner", timeoutTicks = 20)
    public static void startsOnlyOnNetBlocks(GameTestHelper h) {
        build(h);
        h.setBlock(new BlockPos(8, 2, 8), Blocks.STONE.defaultBlockState());
        for (BlockPos rel : new BlockPos[] {new BlockPos(4, 2, 2), new BlockPos(2, 2, 2), new BlockPos(6, 2, 2)}) {
            if (NetScannerItem.starts(h.getLevel(), h.absolutePos(rel)) == null) throw new GameTestAssertException("kein Startfeld bei " + rel);
        }
        if (NetScannerItem.starts(h.getLevel(), h.absolutePos(new BlockPos(8, 2, 8))) != null) throw new GameTestAssertException("Stein ist Startfeld");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "netscanner", timeoutTicks = 20)
    public static void payloadRoundTrip(GameTestHelper h) {
        build(h);
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setPos(h.absoluteVec(new BlockPos(4, 2, 3).getCenter()));
        BlockPos start = h.absolutePos(new BlockPos(4, 2, 2));
        NetScanPayload p = NetScannerItem.payload(h.getLevel(), NetScannerItem.starts(h.getLevel(), start), player);
        if (p.cables().length != 3 || p.devices().length != 1 || p.machines().size() != 1) {
            throw new GameTestAssertException("Paket: " + p.cables().length + " Kabel, " + p.devices().length + " Geraete, " + p.machines().size() + " Anlagen");
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        NetScanPayload.STREAM_CODEC.encode(buf, p);
        NetScanPayload q = NetScanPayload.STREAM_CODEC.decode(buf);
        NetScanPayload.Machine a = p.machines().get(0), b = q.machines().get(0);
        if (!a.pos().equals(b.pos()) || !a.port().equals(b.port()) || a.state() != b.state() || !a.name().equals(b.name())) {
            throw new GameTestAssertException("Anlage nach dem Lesen anders");
        }
        if (!java.util.Arrays.equals(p.cables(), q.cables()) || buf.isReadable()) throw new GameTestAssertException("Kabelliste nach dem Lesen anders");
        if (Component.empty().equals(b.name())) throw new GameTestAssertException("Name leer");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "netscanner", timeoutTicks = 20)
    public static void itemIsRegistered(GameTestHelper h) {
        if (!CatalogRegistry.NET_SCANNER.isBound()) throw new GameTestAssertException("Item nicht registriert");
        h.succeed();
    }
}
