package com.xenowarriors.vehicle;

/** The local driver's extra controls (set by the client each tick): helicopter up (jump) / down (sprint key). */
public final class VehicleControls {
    public static volatile boolean up, down;

    private VehicleControls() {}
}
