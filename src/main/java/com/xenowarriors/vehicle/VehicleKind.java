package com.xenowarriors.vehicle;

/** The four vehicles: seats, top speed (blocks/tick), acceleration, health, price at the dealer (emeralds), colours. */
public enum VehicleKind {
    CAR("car", "Car", 0.62, 0.02, 60, 24, new String[]{"red", "blue", "black", "white", "yellow"}, 2.0F, 1.5F, 1.45F),
    MOTORBIKE("motorbike", "Motorbike", 0.78, 0.03, 30, 12, new String[]{"red", "black", "green", "orange"}, 1.0F, 1.3F, 1.3F),
    HELICOPTER("helicopter", "Helicopter", 0.70, 0.018, 70, 64, new String[]{"skyblue", "white", "black"}, 2.2F, 2.0F, 1.45F),
    GOV_TRUCK("gov_truck", "Armored Truck", 0.48, 0.012, 140, 48, new String[]{"gov"}, 2.3F, 2.3F, 1.35F);

    /** How a rider rides a spot: sitting inside, standing on top (when the seats are full), or hanging on a rail (helicopter). */
    public enum SeatType { SIT, STAND, HANG }

    /** One riding spot: x right, y up, z forward, in blocks (already scaled with the vehicle). */
    public record Seat(double x, double y, double z, SeatType type) {}

    public final String id, title;
    /** How many can ride at once (seats inside first, then the spots on top / on the rails). */
    public final int seats, health, price;
    public final double maxSpeed, accel;
    public final String[] variants;
    public final float width, height;
    /** How much bigger than the model the vehicle is drawn (and its hitbox and seats), so characters fit inside. */
    public final float scale;
    private final Seat[] spots;

    VehicleKind(String id, String title, double maxSpeed, double accel, int health, int price, String[] variants, float width, float height, float scale) {
        this.id = id; this.title = title; this.maxSpeed = maxSpeed; this.accel = accel; this.health = health; this.price = price;
        this.variants = variants; this.width = width * scale; this.height = height * scale; this.scale = scale;
        double[][] raw = rawSeats();
        spots = new Seat[raw.length];
        for (int i = 0; i < raw.length; i++)
            spots[i] = new Seat(raw[i][0] * scale, raw[i][1] * scale, raw[i][2] * scale, SeatType.values()[(int) raw[i][3]]);
        this.seats = spots.length;
    }

    public String variant(int i) { return variants[Math.floorMod(i, variants.length)]; }

    /** The riding spots, in boarding order (the first one drives). */
    public Seat[] seatList() { return spots.clone(); }

    public Seat seat(int i) { return spots[Math.max(0, Math.min(spots.length - 1, i))]; }

    /** How many sit inside (the rest stand on top or hang on). */
    public int insideSeats() {
        int n = 0;
        for (Seat s : spots) if (s.type == SeatType.SIT) n++;
        return n;
    }

    // x, y, z (model blocks, before scaling), type (0 sit, 1 stand on top, 2 hang on the rail)
    private double[][] rawSeats() {
        return switch (this) {
            // 4 inside, then 2 standing on the roof
            case CAR -> new double[][]{{-0.38, -0.05, 0.05, 0}, {0.38, -0.05, 0.05, 0}, {-0.38, -0.05, -0.7, 0}, {0.38, -0.05, -0.7, 0},
                    {-0.32, 1.38, -0.25, 1}, {0.32, 1.38, -0.25, 1}};
            // rider and pillion, then 2 standing on the back
            case MOTORBIKE -> new double[][]{{0, 0.45, -0.15, 0}, {0, 0.5, -0.6, 0},
                    {-0.2, 0.95, -0.92, 1}, {0.2, 0.95, -0.92, 1}};
            // 6 in the cabin (three rows), then 4 hanging from the skids (two a side) - the rotor's on top, so nobody stands there
            case HELICOPTER -> new double[][]{{-0.38, 0.2, 0.6, 0}, {0.38, 0.2, 0.6, 0}, {-0.38, 0.2, -0.12, 0}, {0.38, 0.2, -0.12, 0},
                    {-0.38, 0.2, -0.8, 0}, {0.38, 0.2, -0.8, 0},
                    {-0.98, HANG_Y, 0.45, 2}, {0.98, HANG_Y, 0.45, 2}, {-0.98, HANG_Y, -0.45, 2}, {0.98, HANG_Y, -0.45, 2}};
            // 4 in the cab, then 4 standing on the cargo box
            case GOV_TRUCK -> new double[][]{{-0.45, 0.25, 1.45, 0}, {0.45, 0.25, 1.45, 0}, {-0.5, 0.25, 0.2, 0}, {0.5, 0.25, 0.2, 0},
                    {-0.45, 2.13, -0.5, 1}, {0.45, 2.13, -0.5, 1}, {-0.45, 2.13, -1.6, 1}, {0.45, 2.13, -1.6, 1}};
        };
    }

    /** Hanging from the helicopter skids: hands up on the skid (~0.13 up in model blocks), so the feet are ~2 blocks under it. */
    private static final double HANG_Y = -1.29;     // ((0.18 - 2.05) / 1.45)

    /** How far up the rail is (blocks, scaled) - when the helicopter's on the ground, the hangers stand beside it instead. */
    public static final double SKID_TOP = 0.18;

    public static VehicleKind byId(String id) {
        for (VehicleKind k : values()) if (k.id.equals(id)) return k;
        return CAR;
    }
}
