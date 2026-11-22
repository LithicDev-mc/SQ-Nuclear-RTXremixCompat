package com.sqc.nuclearsq.apps.client;

import com.sqc.nuclearsq.apps.AppPayloads;

public final class ClientAppSession {
    private ClientAppSession() {}

    private static boolean loggedIn;
    private static String name = "";

    public static boolean loggedIn() {
        return loggedIn;
    }

    public static String name() {
        return name;
    }

    public static void receive(AppPayloads.Session s) {
        loggedIn = s.loggedIn();
        name = s.name();
    }

    public static void receive(AppPayloads.State s) {
        loggedIn = s.loggedIn();
        name = s.name();
    }
}
