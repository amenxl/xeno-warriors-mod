package com.xenowarriors.client;

/** Your Money balance, as the server last said. */
public final class ClientMoney {
    private static long money;
    private ClientMoney() {}
    public static void set(long v) { money = v; }
    public static long get() { return money; }
}
