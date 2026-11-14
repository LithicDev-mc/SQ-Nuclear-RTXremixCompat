package com.sqc.nuclearsq.gametest;

import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.data.AreaMonitorBlockEntity;
import com.sqc.nuclearsq.data.DataPanelBlock;
import com.sqc.nuclearsq.data.DataRegistry;
import com.sqc.nuclearsq.data.DataSource;
import com.sqc.nuclearsq.data.DataSources;
import com.sqc.nuclearsq.data.LogEntry;
import com.sqc.nuclearsq.data.LogbookBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class LogbookTests {
    private LogbookTests() {}

    private static final String FLOOR = "floor";

    private static LogbookBlockEntity logbook(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, DataRegistry.LOGBOOK.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.WEST));
        if (!(h.getLevel().getBlockEntity(h.absolutePos(rel)) instanceof LogbookBlockEntity be)) {
            throw new GameTestAssertException("Kein Logbuch bei " + rel);
        }
        return be;
    }

    private static LogEntry last(LogbookBlockEntity be) {
        if (be.entries().isEmpty()) throw new GameTestAssertException("Logbuch leer");
        return be.entries().get(be.entries().size() - 1);
    }

    @GameTest(template = FLOOR, batch = "logbook_events", timeoutTicks = 260)
    public static void recordsAlarmAndAllClear(GameTestHelper h) {
        BlockPos book = new BlockPos(2, 2, 2), mon = new BlockPos(5, 2, 2);
        LogbookBlockEntity be = logbook(h, book);
        h.setBlock(new BlockPos(3, 2, 2), DataRegistry.CABLE.get().defaultBlockState());
        h.setBlock(new BlockPos(4, 2, 2), DataRegistry.CABLE.get().defaultBlockState());
        h.setBlock(mon, DataRegistry.AREA_MONITOR.get().defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.NORTH));
        BlockPos abs = h.absolutePos(mon);
        if (!(h.getLevel().getBlockEntity(abs) instanceof AreaMonitorBlockEntity area)) throw new GameTestAssertException("Kein Messgeraet");
        h.runAfterDelay(3, () -> {
            area.setThreshold(1e6);
            area.measure();
            be.scan();
            if (be.entries().size() != 1 || last(be).severity() != LogEntry.INFO) {
                throw new GameTestAssertException("Erster Lauf soll genau den Start-Eintrag schreiben: " + be.entries().size());
            }
            if (last(be).millis() <= 0) throw new GameTestAssertException("Keine Echtzeit im Eintrag");
            DataSource s = DataSources.at(h.getLevel(), abs);
            if (s == null || s.state() == DataSource.ALARM) throw new GameTestAssertException("Messgeraet steht schon auf Alarm");
        });
        h.runAfterDelay(70, () -> {
            area.setThreshold(AreaMonitorBlockEntity.MIN_THRESHOLD);
            area.measure();
            if (!area.alarm()) throw new GameTestAssertException("Messgeraet loest keinen Alarm aus");
            be.scan();
            if (be.entries().size() != 2 || last(be).severity() != LogEntry.ALARM) {
                throw new GameTestAssertException("Alarm fehlt im Logbuch: " + be.entries().size());
            }
            String text = last(be).text().getString();
            if (!text.contains(DataSources.at(h.getLevel(), abs).name().getString())) throw new GameTestAssertException("Anlagenname fehlt: " + text);
            be.scan();
            if (be.entries().size() != 2) throw new GameTestAssertException("Gleicher Zustand wurde doppelt eingetragen");
        });
        h.runAfterDelay(140, () -> {
            area.setThreshold(1e6);
            area.measure();
            be.scan();
            if (be.entries().size() != 3 || last(be).severity() != LogEntry.CLEAR) {
                throw new GameTestAssertException("Entwarnung fehlt im Logbuch: " + be.entries().size());
            }
            var tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
            LogbookBlockEntity copy = new LogbookBlockEntity(be.getBlockPos(), be.getBlockState());
            copy.setLevel(h.getLevel());
            copy.loadWithComponents(tag, h.getLevel().registryAccess());
            if (copy.entries().size() != 3 || copy.nextSeq() != be.nextSeq()) throw new GameTestAssertException("Eintraege nach dem Laden verloren");
            if (!copy.entries().get(1).text().equals(be.entries().get(1).text())) throw new GameTestAssertException("Text nach dem Laden anders");
            copy.scan();
            if (copy.entries().size() != 3) throw new GameTestAssertException("Neu geladenes Buch schreibt doppelt: " + copy.entries().size());
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "logbook_cap", timeoutTicks = 20)
    public static void keepsOnlyNewestEntries(GameTestHelper h) {
        LogbookBlockEntity be = logbook(h, new BlockPos(2, 2, 2));
        for (int i = 0; i < LogbookBlockEntity.MAX_ENTRIES + 20; i++) be.add(LogEntry.INFO, Component.literal("e" + i));
        if (be.entries().size() != LogbookBlockEntity.MAX_ENTRIES) throw new GameTestAssertException("Obergrenze greift nicht: " + be.entries().size());
        if (!last(be).text().getString().equals("e" + (LogbookBlockEntity.MAX_ENTRIES + 19))) throw new GameTestAssertException("Neuester Eintrag nicht hinten");
        if (be.entries().get(0).seq() != 21) throw new GameTestAssertException("Aelteste nicht entfernt: " + be.entries().get(0).seq());
        List<LogEntry> since = be.since(be.nextSeq() - 3);
        if (since.size() != 3) throw new GameTestAssertException("since() liefert " + since.size());
        be.clear();
        if (!be.entries().isEmpty()) throw new GameTestAssertException("clear() leert nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "logbook_wall", timeoutTicks = 20)
    public static void groupsIntoOneScreen(GameTestHelper h) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(2, 2, 3);
        LogbookBlockEntity first = logbook(h, a);
        h.setBlock(b, DataRegistry.LOGBOOK.get().defaultBlockState().setValue(DataPanelBlock.FACING, Direction.WEST));
        var layout = com.sqc.nuclearsq.data.LogbookBlock.layout(h.getLevel(), h.absolutePos(b));
        if (layout.w() != 2 || layout.h() != 1) throw new GameTestAssertException("Kein 2x1-Bildschirm: " + layout.w() + "x" + layout.h());
        if (com.sqc.nuclearsq.data.LogbookBlock.book(h.getLevel(), h.absolutePos(b)) != h.getLevel().getBlockEntity(layout.origin())) {
            throw new GameTestAssertException("book() liefert nicht den Ursprung");
        }
        first.add(LogEntry.INFO, Component.literal("x"));
        if (!(h.getLevel().getBlockEntity(h.absolutePos(b)) instanceof LogbookBlockEntity second) || !second.entries().isEmpty()) {
            throw new GameTestAssertException("Zweiter Block fuehrt ein eigenes Buch");
        }
        var tag = layout.origin().equals(h.absolutePos(a)) ? first.getUpdateTag(h.getLevel().registryAccess()) : second.getUpdateTag(h.getLevel().registryAccess());
        if (tag.getList("entries", 10).size() != 1) throw new GameTestAssertException("Update-Tag ohne Eintrag");
        h.succeed();
    }
}
