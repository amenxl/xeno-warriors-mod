package com.xenowarriors.vehicle;

/** The four vehicles: seats, top speed (blocks/tick), acceleration, health, price at the dealer (emeralds), colours. */
public enum VehicleKind {
    CAR("car", "Car", 4, 0.62, 0.02, 60, 24, new String[]{"red", "blue", "black", "white", "yellow"}, 2.0F, 1.5F),
    MOTORBIKE("motorbike", "Motorbike", 2, 0.78, 0.03, 30, 12, new String[]{"red", "black", "green", "orange"}, 1.0F, 1.3F),
    HELICOPTER("helicopter", "Helicopter", 4, 0.70, 0.018, 70, 64, new String[]{"skyblue", "white", "black"}, 2.2F, 2.0F),
    GOV_TRUCK("gov_truck", "Armored Truck", 4, 0.48, 0.012, 140, 48, new String[]{"gov"}, 2.3F, 2.3F);

    public final String id, title;
    public final int seats, health, price;
    public final double maxSpeed, accel;
    public final String[] variants;
    public final float width, height;

    VehicleKind(String id, String title, int seats, double maxSpeed, double accel, int health, int price, String[] variants, float width, float height) {
        this.id = id; this.title = title; this.seats = seats; this.maxSpeed = maxSpeed; this.accel = accel; this.health = health; this.price = price;
        this.variants = variants; this.width = width; this.height = height;
    }

    public String variant(int i) { return variants[Math.floorMod(i, variants.length)]; }

    /** Seat positions (x right, y up, z forward) in blocks, per seat. */
    public double[][] seats() {
        return switch (this) {
            case CAR -> new double[][]{{-0.38, 0.35, 0.05}, {0.38, 0.35, 0.05}, {-0.38, 0.35, -0.7}, {0.38, 0.35, -0.7}};
            case MOTORBIKE -> new double[][]{{0, 0.55, -0.15}, {0, 0.6, -0.6}};
            case HELICOPTER -> new double[][]{{-0.38, 0.45, 0.6}, {0.38, 0.45, 0.6}, {-0.38, 0.45, -0.25}, {0.38, 0.45, -0.25}};
            case GOV_TRUCK -> new double[][]{{-0.45, 0.6, 1.45}, {0.45, 0.6, 1.45}, {-0.5, 0.6, 0.2}, {0.5, 0.6, 0.2}};
        };
    }

    public static VehicleKind byId(String id) {
        for (VehicleKind k : values()) if (k.id.equals(id)) return k;
        return CAR;
    }
}
