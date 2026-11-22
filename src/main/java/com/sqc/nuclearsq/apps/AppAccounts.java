package com.sqc.nuclearsq.apps;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class AppAccounts extends SavedData {
    private static final String KEY = "nuclearsq_app_accounts";
    private static final SavedData.Factory<AppAccounts> FACTORY = new SavedData.Factory<>(AppAccounts::new, AppAccounts::load, null);
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final int MIN_LEN = 4, MAX_LEN = 32, MAX_FAILS = 5;
    public static final long LOCK_TICKS = 20L * 60;

    public enum Result {
        OK, EMPTY, TOO_SHORT, TOO_LONG, MISMATCH, EXISTS, NO_ACCOUNT, WRONG, LOCKED, SAME;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final class Account {
        String name;
        String salt;
        String hash;
        int fails;
        long lockedUntil;

        Account(String name, String salt, String hash) {
            this.name = name;
            this.salt = salt;
            this.hash = hash;
        }
    }

    private final Map<UUID, Account> accounts = new HashMap<>();

    public static AppAccounts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    public boolean has(UUID id) {
        return accounts.containsKey(id);
    }

    public int size() {
        return accounts.size();
    }

    public long lockedFor(UUID id, long now) {
        Account a = accounts.get(id);
        return a == null ? 0 : Math.max(0, a.lockedUntil - now);
    }

    @Nullable
    private static Result validate(String pw, String repeat) {
        if (pw == null || pw.isEmpty()) return Result.EMPTY;
        if (pw.length() < MIN_LEN) return Result.TOO_SHORT;
        if (pw.length() > MAX_LEN) return Result.TOO_LONG;
        if (!pw.equals(repeat)) return Result.MISMATCH;
        return null;
    }

    public Result create(UUID id, String name, String pw, String repeat) {
        if (accounts.containsKey(id)) return Result.EXISTS;
        Result bad = validate(pw, repeat);
        if (bad != null) return bad;
        String salt = newSalt();
        accounts.put(id, new Account(name, salt, hash(salt, pw)));
        setDirty();
        return Result.OK;
    }

    public Result login(UUID id, String name, String pw, long now) {
        Account a = accounts.get(id);
        if (a == null) return Result.NO_ACCOUNT;
        if (a.lockedUntil > now) return Result.LOCKED;
        if (pw == null || pw.isEmpty()) return Result.EMPTY;
        if (!a.hash.equals(hash(a.salt, pw))) {
            if (++a.fails >= MAX_FAILS) {
                a.fails = 0;
                a.lockedUntil = now + LOCK_TICKS;
                setDirty();
                return Result.LOCKED;
            }
            setDirty();
            return Result.WRONG;
        }
        a.fails = 0;
        a.lockedUntil = 0;
        if (!a.name.equals(name)) a.name = name;
        setDirty();
        return Result.OK;
    }

    public Result change(UUID id, String old, String pw, String repeat, long now) {
        Account a = accounts.get(id);
        if (a == null) return Result.NO_ACCOUNT;
        Result check = login(id, a.name, old, now);
        if (check != Result.OK) return check;
        Result bad = validate(pw, repeat);
        if (bad != null) return bad;
        if (pw.equals(old)) return Result.SAME;
        a.salt = newSalt();
        a.hash = hash(a.salt, pw);
        setDirty();
        return Result.OK;
    }

    public boolean reset(UUID id) {
        boolean had = accounts.remove(id) != null;
        if (had) setDirty();
        return had;
    }

    private static String newSalt() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static String hash(String salt, String pw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((salt + pw).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AppAccounts load(CompoundTag tag, HolderLookup.Provider registries) {
        AppAccounts d = new AppAccounts();
        CompoundTag all = tag.getCompound("accounts");
        for (String k : all.getAllKeys()) {
            CompoundTag t = all.getCompound(k);
            UUID id;
            try {
                id = UUID.fromString(k);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!t.contains("salt", Tag.TAG_STRING) || !t.contains("hash", Tag.TAG_STRING)) continue;
            Account a = new Account(t.getString("name"), t.getString("salt"), t.getString("hash"));
            a.fails = t.getInt("fails");
            a.lockedUntil = t.getLong("locked");
            d.accounts.put(id, a);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        for (var e : accounts.entrySet()) {
            Account a = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putString("name", a.name);
            t.putString("salt", a.salt);
            t.putString("hash", a.hash);
            if (a.fails > 0) t.putInt("fails", a.fails);
            if (a.lockedUntil > 0) t.putLong("locked", a.lockedUntil);
            all.put(e.getKey().toString(), t);
        }
        tag.put("accounts", all);
        return tag;
    }
}
