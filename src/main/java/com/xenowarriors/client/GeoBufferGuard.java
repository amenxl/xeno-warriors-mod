package com.xenowarriors.client;

/** (Debug) how many times {@code GeckoBufferGuardMixin} handed a GeckoLib bone a fresh buffer instead of a closed one. */
public final class GeoBufferGuard {
    private GeoBufferGuard() {}
    public static int saved;
}
