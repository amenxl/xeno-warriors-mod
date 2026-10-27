package com.xenowarriors.client.mikiko;

/**
 * Mikiko's attack moves. Each has a wind-up pose and a strike pose for his limbs, plus a whole-body
 * motion (spins, flips, lunges, hops). Pose arrays are 13 values in radians:
 * right arm x,y,z | left arm x,y,z | right leg x,y,z | left leg x,y,z | torso twist.
 */
public enum Move {
    // ---- punches ----
    JAB_R(7, Kind.PUNCH, false,
            p(-0.9F, 0.25F, 0.1F, -1.75F, 0.5F, 0F, 0.3F, 0, 0.08F, -0.3F, 0, -0.08F, -0.35F),
            p(-1.62F, 0F, 0F, -1.6F, 0.6F, 0.1F, 0.4F, 0, 0.08F, -0.45F, 0, -0.08F, 0.5F)),
    JAB_L(7, Kind.PUNCH, false,
            p(-1.75F, -0.5F, 0F, -0.9F, -0.25F, -0.1F, -0.3F, 0, 0.08F, 0.3F, 0, -0.08F, 0.35F),
            p(-1.6F, -0.6F, -0.1F, -1.62F, 0F, 0F, -0.45F, 0, 0.08F, 0.4F, 0, -0.08F, -0.5F)),
    HOOK_R(8, Kind.PUNCH, false,
            p(-1.45F, 1.0F, 0.25F, -1.75F, 0.5F, 0F, 0.3F, 0, 0.08F, -0.3F, 0, -0.08F, -0.6F),
            p(-1.5F, -0.7F, 0F, -1.6F, 0.6F, 0.1F, 0.35F, 0, 0.08F, -0.4F, 0, -0.08F, 0.7F)),
    UPPERCUT_L(8, Kind.PUNCH, false,
            p(-1.75F, -0.5F, 0F, 0.2F, 0F, -0.2F, -0.25F, 0, 0.08F, 0.25F, 0, -0.08F, 0.3F),
            p(-1.6F, -0.6F, -0.1F, -2.3F, 0.25F, 0F, -0.2F, 0, 0.08F, 0.2F, 0, -0.08F, -0.3F)),
    /** Dashing in: a long step forward, low, the right fist driven straight through (combo hit 3). */
    LUNGE_PUNCH_R(9, Kind.PUNCH, false,
            p(-0.45F, 0.35F, 0.15F, -1.45F, 0.3F, -0.1F, 0.45F, 0, 0.1F, -0.55F, 0, -0.1F, -0.45F),
            p(-1.62F, 0F, 0F, 0.35F, 0F, -0.25F, 0.7F, 0, 0.12F, -0.85F, 0, -0.1F, 0.55F)),
    /** ...then the left, stepping through again (combo hit 4). */
    LUNGE_PUNCH_L(9, Kind.PUNCH, false,
            p(-1.45F, -0.3F, 0.1F, -0.45F, -0.35F, -0.15F, -0.55F, 0, 0.1F, 0.45F, 0, -0.1F, 0.45F),
            p(0.35F, 0F, 0.25F, -1.62F, 0F, 0F, -0.85F, 0, 0.1F, 0.7F, 0, -0.12F, -0.55F)),
    /** Springing up into a forward somersault and bringing a heel crashing down (combo hit 5). */
    FLIP_KICK(13, Kind.KICK, false,
            p(-2.7F, 0F, 0.3F, -2.7F, 0F, -0.3F, -1.3F, 0, 0.05F, -1.3F, 0, -0.05F, 0F),
            p(-0.3F, 0F, 1.1F, -0.3F, 0F, -1.1F, -2.1F, 0, 0.05F, 0.45F, 0, -0.05F, 0F)),
    /**
     * Tie Shan Kao (Tetsuzanko) - the combo finisher: sinking into a deep stance, he steps in and drives his shoulder
     * and back into them, the lead arm thrown up overhead and the other palm pushing down at his hip. Sends them flying.
     */
    TIE_SHAN_KAO(14, Kind.PUNCH, false,
            p(-1.25F, -0.55F, 0F, -0.85F, 0.45F, 0F, -0.25F, 0, 0.15F, 0.3F, 0, -0.15F, -0.75F),
            p(-2.85F, 0.15F, 0.35F, 0.45F, 0F, -0.55F, -0.95F, 0, 0.3F, 0.85F, 0, -0.2F, 1.05F)),
    // ---- kicks ----
    KICK_FRONT(9, Kind.KICK, false,
            p(-0.4F, 0F, 0.5F, -0.4F, 0F, -0.5F, -0.9F, 0, 0F, 0.1F, 0, 0F, 0F),
            p(-0.1F, 0F, 0.75F, -0.1F, 0F, -0.75F, -1.65F, 0, 0.05F, 0.15F, 0, 0F, 0F)),
    KICK_ROUNDHOUSE(10, Kind.KICK, false,
            p(-0.3F, 0F, 0.6F, -0.6F, 0F, -0.7F, -0.8F, 0, 0.6F, 0.1F, 0, -0.05F, -0.3F),
            p(0F, 0F, 0.9F, -0.4F, 0F, -1.0F, -1.3F, 0, 1.35F, 0.1F, 0, -0.1F, 0.3F)),
    KICK_SPIN(11, Kind.KICK, false,
            p(-0.2F, 0F, 1.0F, -0.2F, 0F, -1.0F, 0F, 0, 0.4F, 0F, 0, 0F, 0F),
            p(-0.2F, 0F, 1.2F, -0.2F, 0F, -1.2F, -0.3F, 0, 1.45F, 0.1F, 0, -0.1F, 0F)),
    KICK_SIDE(9, Kind.KICK, false,
            p(-0.3F, 0F, 0.4F, -1.75F, 0.5F, 0F, -0.6F, 0, 0.7F, 0F, 0, -0.1F, 0F),
            p(-0.3F, 0F, 0.6F, -1.6F, 0.6F, 0.1F, -0.15F, 0, 1.55F, 0F, 0, -0.15F, 0F)),
    KNEE_FLYING(9, Kind.KICK, false,
            p(0.15F, 0F, 0.3F, 0.15F, 0F, -0.3F, -0.3F, 0, 0F, 0.3F, 0, 0F, 0F),
            p(0.35F, 0F, 0.35F, 0.35F, 0F, -0.35F, -2.0F, 0, 0F, 0.7F, 0, 0F, 0F)),
    // ---- blade slashes (Hekublade summoned if his hand is empty; also used with any sword) ----
    BLADE_H(9, Kind.SLASH_H, true,
            p(-1.5F, 1.25F, 0.2F, -0.6F, 0F, -0.5F, 0.25F, 0, 0.08F, -0.25F, 0, -0.08F, -0.7F),
            p(-1.45F, -1.3F, 0F, -0.4F, 0F, -0.7F, 0.3F, 0, 0.08F, -0.3F, 0, -0.08F, 0.8F)),
    BLADE_DOWN(9, Kind.SLASH_DOWN, true,
            p(-2.6F, 0.2F, 0.1F, -0.5F, 0F, -0.4F, 0.1F, 0, 0.05F, -0.2F, 0, -0.05F, -0.2F),
            p(-0.35F, 0F, -0.05F, -0.2F, 0F, -0.6F, 0.35F, 0, 0.08F, -0.45F, 0, -0.08F, 0.2F)),
    BLADE_UP(9, Kind.SLASH_UP, true,
            p(-0.2F, 0.3F, 0.45F, -0.4F, 0F, -0.5F, 0.3F, 0, 0.08F, -0.3F, 0, -0.08F, -0.4F),
            p(-2.25F, -0.4F, -0.2F, -0.2F, 0F, -0.8F, -0.1F, 0, 0.05F, 0.2F, 0, -0.05F, 0.5F)),
    BLADE_SPIN(12, Kind.SLASH_SPIN, true,
            p(-1.5F, 0.9F, 0.3F, -0.3F, 0F, -1.0F, 0.2F, 0, 0.1F, -0.2F, 0, -0.1F, -0.4F),
            p(-1.5F, 0F, 0.25F, -0.3F, 0F, -1.0F, 0.1F, 0, 0.2F, -0.1F, 0, -0.2F, 0F)),
    BLADE_THRUST(9, Kind.THRUST, true,
            p(-1.2F, 0.45F, 0.35F, -0.5F, 0F, -0.5F, 0.3F, 0, 0.08F, -0.3F, 0, -0.08F, -0.5F),
            p(-1.6F, 0F, 0F, -0.2F, 0F, -0.45F, 0.5F, 0, 0.08F, -0.55F, 0, -0.08F, 0.35F)),
    // ---- in the air ----
    AIR_PUNCH_R(8, Kind.PUNCH, false,
            p(-0.9F, 0.25F, 0.1F, -1.75F, 0.5F, 0F, -1.1F, 0, 0.05F, -0.3F, 0, -0.05F, -0.35F),
            p(-1.62F, 0F, 0F, -1.6F, 0.6F, 0.1F, -1.1F, 0, 0.05F, -0.3F, 0, -0.05F, 0.5F)),
    AIR_PUNCH_L(8, Kind.PUNCH, false,
            p(-1.75F, -0.5F, 0F, -0.9F, -0.25F, -0.1F, -0.3F, 0, 0.05F, -1.1F, 0, -0.05F, 0.35F),
            p(-1.6F, -0.6F, -0.1F, -1.62F, 0F, 0F, -0.3F, 0, 0.05F, -1.1F, 0, -0.05F, -0.5F)),
    AIR_SPIN_KICK(11, Kind.KICK, false,
            p(-0.2F, 0F, 1.0F, -0.2F, 0F, -1.0F, -0.2F, 0, 0.4F, -1.2F, 0, 0F, 0F),
            p(-0.2F, 0F, 1.2F, -0.2F, 0F, -1.2F, -0.3F, 0, 1.45F, -1.2F, 0, -0.1F, 0F)),
    /** Like the reference art: leaning down into a big diagonal slash, legs tucked, free hand reaching out. */
    AIR_LUNGE_SLASH(11, Kind.SLASH_DIAG, true,
            p(-2.6F, 0.3F, 0.7F, -1.3F, 0F, -0.6F, -1.4F, 0, 0.05F, 0.5F, 0, -0.05F, -0.4F),
            p(-0.4F, -0.9F, -0.4F, -1.4F, 0F, -0.8F, -1.4F, 0, 0.05F, 0.5F, 0, -0.05F, 0.4F)),
    AIR_FLIP_SLASH(12, Kind.SLASH_FLIP, true,
            p(-2.6F, 0F, 0.1F, -0.5F, 0F, -0.5F, -1.5F, 0, 0.1F, -1.3F, 0, -0.1F, 0F),
            p(-0.8F, 0F, 0F, -0.5F, 0F, -0.5F, -1.5F, 0, 0.1F, -1.3F, 0, -0.1F, 0F));

    public enum Kind { PUNCH, KICK, SLASH_H, SLASH_DOWN, SLASH_UP, SLASH_SPIN, THRUST, SLASH_DIAG, SLASH_FLIP }

    public final int duration;
    public final Kind kind;
    public final boolean blade;
    final float[] wind, strike;

    Move(int duration, Kind kind, boolean blade, float[] wind, float[] strike) {
        this.duration = duration;
        this.kind = kind;
        this.blade = blade;
        this.wind = wind;
        this.strike = strike;
    }

    public boolean isSlash() {
        return kind != Kind.PUNCH && kind != Kind.KICK;
    }

    /** The same move done while flying. */
    public Move inAir() {
        return switch (this) {
            case JAB_R, HOOK_R, LUNGE_PUNCH_R, TIE_SHAN_KAO -> AIR_PUNCH_R;
            case JAB_L, UPPERCUT_L, LUNGE_PUNCH_L -> AIR_PUNCH_L;
            case KICK_FRONT, KICK_ROUNDHOUSE, KICK_SPIN, KICK_SIDE, KNEE_FLYING, FLIP_KICK -> AIR_SPIN_KICK;
            case BLADE_DOWN, BLADE_THRUST -> AIR_LUNGE_SLASH;
            case BLADE_SPIN, BLADE_UP -> AIR_FLIP_SLASH;
            default -> this;
        };
    }

    /**
     * Whole-body motion at progress p (0..1): {lift (blocks), forward (blocks), spin (deg),
     * lean forward (deg), roll (deg)}. S = strike progress 0..1.
     */
    float[] body(float p, float s) {
        float arc = (float) Math.sin(Math.min(1F, p / 0.8F) * Math.PI);
        return switch (this) {
            case JAB_R, JAB_L, HOOK_R -> new float[]{0, 0.15F * s * (1 - p), 0, 4 * s, 0};
            case UPPERCUT_L -> new float[]{0.15F * arc, 0, 0, -8 * s, 0};
            case LUNGE_PUNCH_R, LUNGE_PUNCH_L -> new float[]{-0.12F * s * (1 - fadeOut(p)), 0.2F * s * (1 - fadeOut(p)), 0, 16 * s * (1 - fadeOut(p)), 0};
            case FLIP_KICK -> new float[]{0.55F * arc, 0.15F * s, 0, 360 * spin(p), 0};
            case TIE_SHAN_KAO -> new float[]{-0.2F * s * (1 - fadeOut(p)), 0.5F * s * (1 - fadeOut(p)), 0, -6 * s, 14 * s * (1 - fadeOut(p))};
            case KICK_FRONT -> new float[]{0, 0, 0, -12 * s, 0};
            case KICK_ROUNDHOUSE -> new float[]{0.05F * arc, 0, -70 * arc, 0, -10 * s};
            case KICK_SPIN, AIR_SPIN_KICK -> new float[]{this == KICK_SPIN ? 0.3F * arc : 0, 0, -360 * spin(p), 0, 0};
            case KICK_SIDE -> new float[]{0, 0, -80 * Math.min(1, p * 4) * (1 - fadeOut(p)), 0, 18 * s};
            case KNEE_FLYING -> new float[]{0.45F * arc, 0.3F * s, 0, 10 * s, 0};
            case BLADE_DOWN -> new float[]{0, 0.2F * s * (1 - p), 0, 12 * s, 0};
            case BLADE_UP -> new float[]{0.15F * arc, 0, 0, -6 * s, 0};
            case BLADE_SPIN -> new float[]{0.1F * arc, 0, -360 * spin(p), 0, 0};
            case BLADE_THRUST -> new float[]{0, 0.45F * s * (1 - fadeOut(p)), 0, 8 * s, 0};
            case AIR_PUNCH_R, AIR_PUNCH_L -> new float[]{0, 0.1F * s, 0, 20, 0};
            case AIR_LUNGE_SLASH -> new float[]{0, 0.35F * s, 0, 15 + 25 * s, 12 * s};
            case AIR_FLIP_SLASH -> new float[]{0.2F * arc, 0, 0, 360 * spin(p), 0};
            default -> new float[]{0, 0, 0, 0, 0};
        };
    }

    private static float spin(float p) {
        float x = Math.max(0F, Math.min(1F, (p - 0.15F) / 0.6F));
        return x * x * (3 - 2 * x);
    }

    private static float fadeOut(float p) {
        float x = Math.max(0F, Math.min(1F, (p - 0.7F) / 0.3F));
        return x * x * (3 - 2 * x);
    }

    private static float[] p(float... v) {
        return v;
    }
}
