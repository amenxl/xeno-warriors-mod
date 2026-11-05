package com.xenowarriors.vehicle;

/**
 * The vehicles: seats, top speed (blocks/tick), acceleration, health, price at the dealer (emeralds), colours. The
 * Mk II versions are faster and tougher, and the land ones can fly too (Jump to lift off and climb, the Sprint key
 * to come down). (New kinds go at the END: the dealer sends the index.)
 */
public enum VehicleKind {
    CAR("car", "Car", 0.62, 0.02, 60, 24, new String[]{"red", "blue", "black", "white", "yellow"}, 2.0F, 1.5F, 1.45F),
    MOTORBIKE("motorbike", "Motorbike", 0.78, 0.03, 30, 12, new String[]{"red", "black", "green", "orange"}, 1.0F, 1.3F, 1.3F),
    HELICOPTER("helicopter", "Helicopter", 0.70, 0.018, 70, 64, new String[]{"skyblue", "white", "black"}, 2.2F, 2.0F, 1.45F),
    GOV_TRUCK("gov_truck", "Armored Truck", 0.48, 0.012, 140, 48, new String[]{"gov"}, 2.3F, 2.3F, 1.35F),
    CAR_MK2("car_mk2", "car", "Car Mk II", 0.88, 0.03, 100, 80, new String[]{"red", "blue", "black", "white", "yellow"}, 2.0F, 1.5F, 1.45F),
    MOTORBIKE_MK2("motorbike_mk2", "motorbike", "Motorbike Mk II", 1.05, 0.042, 55, 48, new String[]{"red", "black", "green", "orange"}, 1.0F, 1.3F, 1.3F),
    HELICOPTER_MK2("helicopter_mk2", "helicopter", "Helicopter Mk II", 1.0, 0.028, 130, 160, new String[]{"skyblue", "white", "black"}, 2.2F, 2.0F, 1.45F),
    GOV_TRUCK_MK2("gov_truck_mk2", "gov_truck", "Armored Truck Mk II", 0.7, 0.02, 240, 128, new String[]{"gov"}, 2.3F, 2.3F, 1.35F);

    /** How a rider rides a spot: sitting inside, standing on top (when the seats are full), or hanging on a rail (helicopter). */
    public enum SeatType { SIT, STAND, HANG }

    /** One riding spot: x right, y up, z forward, in blocks (already scaled with the vehicle). */
    public record Seat(double x, double y, double z, SeatType type) {}

    /** id: the entity/item id; model: the base vehicle's id (its model, seats and handling). */
    public final String id, model, title;
    /** How many can ride at once (seats inside first, then the spots on top / on the rails). */
    public final int seats, health, price;
    public final double maxSpeed, accel;
    public final String[] variants;
    public final float width, height;
    /** How much bigger than the model the vehicle is drawn (and its hitbox and seats), so characters fit inside. */
    public final float scale;
    private final Seat[] spots;

    VehicleKind(String id, String title, double maxSpeed, double accel, int health, int price, String[] variants, float width, float height, float scale) {
        this(id, id, title, maxSpeed, accel, health, price, variants, width, height, scale);
    }

    VehicleKind(String id, String model, String title, double maxSpeed, double accel, int health, int price, String[] variants, float width, float height, float scale) {
        this.id = id; this.model = model; this.title = title; this.maxSpeed = maxSpeed; this.accel = accel; this.health = health; this.price = price;
        this.variants = variants; this.width = width * scale; this.height = height * scale; this.scale = scale;
        double[][] raw = rawSeats();
        spots = new Seat[raw.length];
        for (int i = 0; i < raw.length; i++)
            spots[i] = new Seat(raw[i][0] * scale, raw[i][1] * scale, raw[i][2] * scale, SeatType.values()[(int) raw[i][3]]);
        this.seats = spots.length;
    }

    /** The base vehicle (CAR for CAR_MK2, ...): what it looks and handles like. */
    public VehicleKind base() { return byId(model); }

    /** A Mk II. */
    public boolean upgraded() { return !model.equals(id); }

    /** The helicopter (either mark). */
    public boolean heli() { return model.equals("helicopter"); }

    /** A land vehicle that can also fly (the Mk II car, bike and truck). */
    public boolean hovers() { return upgraded() && !heli(); }

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
        return switch (model) {          // (by id: an enum switch here would read values() before the constants exist)
            // 4 inside, then 2 standing on the roof
            case "car" -> new double[][]{{-0.38, -0.05, 0.05, 0}, {0.38, -0.05, 0.05, 0}, {-0.38, -0.05, -0.7, 0}, {0.38, -0.05, -0.7, 0},
                    {-0.32, 1.38, -0.25, 1}, {0.32, 1.38, -0.25, 1}};
            // rider and pillion, then 2 standing on the back
            case "motorbike" -> new double[][]{{0, 0.45, -0.15, 0}, {0, 0.5, -0.6, 0},
                    {-0.2, 0.95, -0.92, 1}, {0.2, 0.95, -0.92, 1}};
            // 6 in the cabin (three rows), then 4 hanging from the skids (two a side) - the rotor's on top, so nobody stands there
            case "helicopter" -> new double[][]{{-0.38, 0.2, 0.6, 0}, {0.38, 0.2, 0.6, 0}, {-0.38, 0.2, -0.12, 0}, {0.38, 0.2, -0.12, 0},
                    {-0.38, 0.2, -0.8, 0}, {0.38, 0.2, -0.8, 0},
                    {-0.98, HANG_Y, 0.45, 2}, {0.98, HANG_Y, 0.45, 2}, {-0.98, HANG_Y, -0.45, 2}, {0.98, HANG_Y, -0.45, 2}};
            // 4 in the cab, then 4 standing on the cargo box
            default -> new double[][]{{-0.45, 0.25, 1.45, 0}, {0.45, 0.25, 1.45, 0}, {-0.5, 0.25, 0.2, 0}, {0.5, 0.25, 0.2, 0},
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
