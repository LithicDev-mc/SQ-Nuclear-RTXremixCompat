package com.sqc.nuclearsq.economy;

import javax.annotation.Nullable;

public final class ActionConfirm {
    public static final long TICKS = 400;

    public enum Result {
        CONSUMED,
        NEW_PENDING,
        STILL_PENDING
    }

    @Nullable
    private String key;
    private long expiry;
    private boolean confirmed;

    public Result check(String actionKey, long now) {
        if (key != null && key.equals(actionKey) && now <= expiry) {
            if (confirmed) {
                clear();
                return Result.CONSUMED;
            }
            return Result.STILL_PENDING;
        }
        key = actionKey;
        expiry = now + TICKS;
        confirmed = false;
        return Result.NEW_PENDING;
    }

    public boolean confirm(long now) {
        if (key == null || now > expiry) {
            clear();
            return false;
        }
        confirmed = true;
        expiry = now + TICKS;
        return true;
    }

    public boolean pending(long now) {
        return key != null && now <= expiry && !confirmed;
    }

    public void clear() {
        key = null;
        confirmed = false;
    }
}
