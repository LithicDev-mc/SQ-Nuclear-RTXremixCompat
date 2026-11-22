package com.sqc.nuclearsq.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.apps.AppAccounts;
import com.sqc.nuclearsq.apps.AppMonitorBlock;
import com.sqc.nuclearsq.apps.AppPayloads;
import com.sqc.nuclearsq.apps.AppRegistry;
import com.sqc.nuclearsq.apps.AppSessions;
import com.sqc.nuclearsq.apps.LoginMonitor;
import com.sqc.nuclearsq.economy.EconomyRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class AppLoginTests {
    private AppLoginTests() {}

    private static final String FLOOR = "floor";
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000a901"), "LoginTester");

    private static void check(boolean ok, String msg) {
        if (!ok) throw new GameTestAssertException(msg);
    }

    @GameTest(template = FLOOR, batch = "app_login", timeoutTicks = 20)
    public static void accountsCheckPasswords(GameTestHelper h) {
        AppAccounts a = new AppAccounts();
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000a902");
        check(a.login(id, "x", "geheim", 0) == AppAccounts.Result.NO_ACCOUNT, "Anmelden ohne Konto");
        check(a.create(id, "x", "abc", "abc") == AppAccounts.Result.TOO_SHORT, "zu kurzes Kennwort angenommen");
        check(a.create(id, "x", "geheim", "geheiM") == AppAccounts.Result.MISMATCH, "verschiedene Kennwoerter angenommen");
        check(a.create(id, "x", "geheim", "geheim") == AppAccounts.Result.OK, "Konto nicht angelegt");
        check(a.create(id, "x", "anders1", "anders1") == AppAccounts.Result.EXISTS, "zweites Konto angelegt");
        check(a.login(id, "x", "geheim", 0) == AppAccounts.Result.OK, "richtiges Kennwort abgelehnt");
        check(a.login(id, "x", "falsch", 0) == AppAccounts.Result.WRONG, "falsches Kennwort angenommen");
        for (int i = 1; i < AppAccounts.MAX_FAILS - 1; i++) a.login(id, "x", "falsch", 0);
        check(a.login(id, "x", "falsch", 0) == AppAccounts.Result.LOCKED, "keine Sperre nach " + AppAccounts.MAX_FAILS + " Fehlversuchen");
        check(a.login(id, "x", "geheim", 10) == AppAccounts.Result.LOCKED, "gesperrt, aber richtiges Kennwort angenommen");
        long after = AppAccounts.LOCK_TICKS + 1;
        check(a.login(id, "x", "geheim", after) == AppAccounts.Result.OK, "Sperre laeuft nicht ab");
        check(a.change(id, "geheim", "neu123", "neu123", after) == AppAccounts.Result.OK, "Kennwort nicht geaendert");
        check(a.login(id, "x", "geheim", after) == AppAccounts.Result.WRONG, "altes Kennwort gilt noch");
        check(a.login(id, "x", "neu123", after) == AppAccounts.Result.OK, "neues Kennwort gilt nicht");
        var tag = a.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
        check(!tag.toString().contains("neu123"), "Kennwort steht im Klartext in den Weltdaten");
        check(a.reset(id) && !a.has(id), "Zuruecksetzen loescht das Konto nicht");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "app_login", timeoutTicks = 40)
    public static void loginMonitorNeedsBankAndLogsIn(GameTestHelper h) {
        BlockPos mon = new BlockPos(2, 2, 2), server = new BlockPos(3, 2, 2);
        h.setBlock(mon, AppRegistry.LOGIN_MONITOR.get().defaultBlockState().setValue(AppMonitorBlock.FACING, Direction.NORTH));
        ServerPlayer p = FakePlayerFactory.get(h.getLevel(), PROFILE);
        BlockPos abs = h.absolutePos(mon);
        p.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() - 1.5);
        AppAccounts acc = AppAccounts.get(h.getLevel().getServer());
        acc.reset(PROFILE.getId());
        AppSessions.logout(p);
        try {
            String m = LoginMonitor.apply(p, new AppPayloads.Action(abs, AppPayloads.CREATE, "geheim", "geheim", ""));
            check("offline".equals(m), "ohne Bank-Server angelegt: " + m);
            check(!acc.has(PROFILE.getId()) && !AppSessions.loggedIn(p), "ohne Bank-Server angemeldet");

            h.setBlock(server, EconomyRegistry.SERVER.get().defaultBlockState());
            m = LoginMonitor.apply(p, new AppPayloads.Action(abs, AppPayloads.CREATE, "geheim", "geheim", ""));
            check("created".equals(m) && AppSessions.loggedIn(p), "Konto anlegen am Server: " + m);

            m = LoginMonitor.apply(p, new AppPayloads.Action(abs, AppPayloads.LOGOUT, "", "", ""));
            check("bye".equals(m) && !AppSessions.loggedIn(p), "Abmelden: " + m);

            m = LoginMonitor.apply(p, new AppPayloads.Action(abs, AppPayloads.CHANGE, "geheim", "neu123", "neu123"));
            check("not_in".equals(m), "Kennwort aendern ohne Anmeldung: " + m);

            m = LoginMonitor.apply(p, new AppPayloads.Action(abs, AppPayloads.LOGIN, "falsch", "", ""));
            check("wrong".equals(m) && !AppSessions.loggedIn(p), "falsches Kennwort: " + m);

            m = LoginMonitor.apply(p, new AppPayloads.Action(abs, AppPayloads.LOGIN, "geheim", "", ""));
            check("welcome".equals(m) && AppSessions.loggedIn(p) && AppSessions.use(p), "Anmelden: " + m);

            p.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() - 20);
            m = LoginMonitor.apply(p, new AppPayloads.Action(abs, AppPayloads.LOGOUT, "", "", ""));
            check("far".equals(m) && AppSessions.loggedIn(p), "Abmelden aus 20 Bloecken Entfernung: " + m);
        } finally {
            acc.reset(PROFILE.getId());
            AppSessions.logout(p);
        }
        h.succeed();
    }
}
