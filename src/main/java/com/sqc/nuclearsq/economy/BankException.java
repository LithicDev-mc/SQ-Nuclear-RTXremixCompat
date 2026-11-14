package com.sqc.nuclearsq.economy;

public final class BankException extends RuntimeException {
    public BankException(String message) {
        super(message, null, false, false);
    }
}
