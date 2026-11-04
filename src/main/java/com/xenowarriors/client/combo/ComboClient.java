package com.xenowarriors.client.combo;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.AttackHooks;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.combat.ComboStyle;
import com.xenowarriors.network.ComboHitPacket;
import com.xenowarriors.network.ComboStartPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Combo combat, your side: left click starts (or queues) the next hit of your string; the hit lands at its strike
 * frame on whoever you're aiming at (or locked on to, or - to keep a string linked - the nearest enemy right in front
 * of you), and the next one starts from the cancel point. Sneak + click is the heavy. You're rooted a little while
 * swinging, and step in on each hit. Mining (clicking a block) and Epic Fight's battle mode are left alone.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ComboClient {
    /** A swing in progress (anyone's - you, other players, bots): entity id -> swing. */
    public record Swing(ComboStyle style, int hit, boolean heavy, int start, int duration) {
        public float progress(float age) { return (age - start) / duration; }
    }
    private static final Map<Integer, Swing> SWINGS = com.xenowarriors.client.ClientReset.track(new HashMap<>());
    private static final Map<Integer, Integer> STARTED_AT = com.xenowarriors.client.ClientReset.track(new HashMap<>());   // id -> client tick a combo swing began

    // your own string
    private static Swing cur;
    private static boolean struck, queued, queuedHeavy;
    private static int lastHit = -1, lastEnd = -1000, lockUntil;
    private static boolean lastWasEnder;
    private static int lastTarget = -1;
    private static int ticks;

    private ComboClient() {}

    // ------------------------------------------------------------------ queries for the animation code

    /** A combo swing started on this entity this tick or the last (so its own attack animation plays). */
    public static boolean comboSwing(Entity e) {
        Integer t = STARTED_AT.get(e.getId());
        return t != null && ticks - t <= 1;
    }

    /** The current swing of an entity, or null. */
    public static Swing swing(Entity e) {
        Swing s = SWINGS.get(e.getId());
        if (s == null) return null;
        if (e.tickCount - s.start() > s.duration() + 2) { SWINGS.remove(e.getId()); return null; }
        return s;
    }

    /** The hit index of a combo swing that just started (for syncing a character's own animation), or -1. */
    public static int hitIndex(Entity e) {
        Swing s = SWINGS.get(e.getId());
        return s != null && comboSwing(e) && !s.heavy() ? s.hit() : -1;
    }

    public static boolean heavy(Entity e) {
        Swing s = SWINGS.get(e.getId());
        return s != null && comboSwing(e) && s.heavy();
    }

    /** Someone else's swing (from the server). */
    public static void remote(int id, int style, int hit, boolean heavy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(id);
        if (e == null || e == mc.player) return;
        ComboStyle st = ComboStyle.values()[Math.floorMod(style, ComboStyle.values().length)];
        SWINGS.put(id, new Swing(st, hit, heavy, e.tickCount, st.duration(hit, heavy)));
        STARTED_AT.put(id, ticks);
    }

    // ------------------------------------------------------------------ input

    private static ComboStyle styleFor(LocalPlayer p) {
        return ComboStyle.of(p, p.getMainHandItem(), ClientCharacterData.get(p));
    }

    private static boolean handles(Minecraft mc, LocalPlayer p) {
        if (mc.screen != null || p.isSpectator() || p.isUsingItem() || !p.isAlive()) return false;
        if (AttackHooks.externalCombat.test(p)) return false;                          // Epic Fight's battle mode
        if (com.xenowarriors.client.compat.DmzCompat.blocking(p)) return false;
        if (p.getVehicle() != null && !(p.getVehicle() instanceof LivingEntity) || p.getVehicle() instanceof com.xenowarriors.vehicle.VehicleEntity) return false;
        HitResult hr = mc.hitResult;
        if (hr != null && hr.getType() == HitResult.Type.BLOCK) return false;           // mining
        if (hr instanceof EntityHitResult ehr && !(ehr.getEntity() instanceof LivingEntity)) return false;   // item frames, boats...
        return styleFor(p) != null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || event.isCanceled() || mc.player == null || !handles(mc, mc.player)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        // Rhalix on the ground: the click is read on release - a tap is the next hit, held it charges (Ki Burst,
        // Rising Leap), held while running it's the Kishi Warp
        if (styleFor(mc.player) == ComboStyle.RHALIX && !mc.player.isShiftKeyDown()) { holding = true; holdStart = ticks; return; }
        queued = true;
        queuedHeavy = mc.player.isShiftKeyDown();
        tryStart(mc.player);
    }

    // ------------------------------------------------------------------ Rhalix's held attacks

    /** Held this long (ticks): a tap below TAP, the half charge (Ki Burst) below FULL, the full charge (Rising Leap) from FULL. */
    public static final int TAP = 7, FULL = 20, KISHI_HOLD = 6;
    private static boolean holding;
    private static int holdStart, testHoldUntil = -1, queuedSpecial = -1;
    private static boolean autoString;

    /** Ticks the attack button has been held (Rhalix charging), or -1. */
    public static int charge() { return holding ? ticks - holdStart : -1; }

    /** The UI test: as if the attack button were pressed now and held for this many ticks. */
    public static void testHold(int holdTicks) {
        holding = true; holdStart = ticks; testHoldUntil = ticks + holdTicks;
    }

    private static boolean attackDown(Minecraft mc) { return mc.options.keyAttack.isDown() || ticks < testHoldUntil; }

    private static void holdTick(Minecraft mc, LocalPlayer p) {
        if (!holding) return;
        int held = ticks - holdStart;
        if (styleFor(p) != ComboStyle.RHALIX) { holding = false; queued = true; queuedHeavy = false; return; }   // (left the ground: a plain click)
        if (attackDown(mc)) {
            if (held >= KISHI_HOLD && p.isSprinting() && cur == null && com.xenowarriors.client.KishiWarpClient.ready()) {
                holding = false;
                testHoldUntil = -1;
                com.xenowarriors.client.KishiWarpClient.fire(p, rushPick(p, 14.5));
            } else if (held >= TAP && held % 2 == 0) com.xenowarriors.client.RhalixComboFx.chargeSparks(p, held >= FULL);
            return;
        }
        holding = false;
        if (held < TAP) { queued = true; queuedHeavy = false; }
        else { queuedSpecial = held < FULL ? ComboStyle.BURST : ComboStyle.LEAP; queued = true; queuedHeavy = false; }
        com.xenowarriors.client.RhalixComboFx.log("release after " + held + " ticks -> " + (held < TAP ? "tap" : held < FULL ? "Ki Burst" : "Rising Leap"));
    }

    /** The Kishi reached someone and he warped to them: the knockdown slash, then the rest of his string, by itself. */
    public static void warpString(LocalPlayer p, int targetId) {
        lastTarget = targetId;
        cur = null; queued = false; holding = false; queuedSpecial = -1;
        start(p, ComboStyle.RHALIX, 4, false, p.tickCount);
        autoString = true;
    }

    /** A click from code (the UI test): exactly like left-clicking at an enemy. */
    public static void press(boolean heavy) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || styleFor(p) == null) return;
        queued = true;
        queuedHeavy = heavy;
        tryStart(p);
    }

    private static void tryStart(LocalPlayer p) {
        if (!queued) return;
        int now = p.tickCount;
        if (cur != null && now - cur.start() < ComboStyle.cancel(cur.duration())) return;   // not yet: it waits its turn
        if (cur != null && lastWasEnderOf(cur)) return;                          // a finisher/heavy plays out in full
        if (now < lockUntil) return;
        if (com.xenowarriors.client.KishiWarpClient.warping()) return;            // knelt, sending the Kishi out
        ComboStyle style = styleFor(p);
        if (autoString && style != null && style.rhalix()) style = ComboStyle.RHALIX;      // (the warp string stays on the ground)
        if (style == null) { queued = false; return; }
        boolean heavy = queuedHeavy;
        int hit;
        int chain = style.rhalix() ? 18 : ComboStyle.CHAIN;                       // (Rhalix: time to run in between and carry on)
        if (queuedSpecial >= 0 && style == ComboStyle.RHALIX) { hit = queuedSpecial; heavy = false; }
        else if (cur != null && !lastWasEnderOf(cur)) hit = next(cur.style(), cur.hit(), style);
        else if (cur == null && now - lastEnd <= chain && lastHit >= 0 && !lastWasEnder) hit = next(lastStyle, lastHit, style);
        else hit = 0;
        if (hit >= style.length() && !style.special(hit)) hit = 0;
        queued = false;
        queuedSpecial = -1;
        start(p, style, hit, heavy, now);
    }

    /** The hit after this one: the string goes on; Rhalix's Ki Burst carries on into the grab, his Rising Leap into the air string. */
    private static int next(ComboStyle was, int hit, ComboStyle style) {
        if (was == ComboStyle.RHALIX && hit == ComboStyle.BURST) return style == ComboStyle.RHALIX ? 5 : 1;
        if (was == ComboStyle.RHALIX && hit == ComboStyle.LEAP) return style == ComboStyle.RHALIX_AIR ? 1 : 0;
        if (was != style) return 0;
        return hit + 1 < style.length() ? hit + 1 : 0;
    }

    private static boolean lastWasEnderOf(Swing s) {
        return s.heavy() || !s.style().special(s.hit()) && s.hit() >= s.style().length() - 1;
    }

    private static ComboStyle lastStyle;

    private static void start(LocalPlayer p, ComboStyle style, int hit, boolean heavy, int now) {
        int dur = style.duration(hit, heavy);
        cur = new Swing(style, hit, heavy, now, dur);
        struck = false;
        SWINGS.put(p.getId(), cur);
        STARTED_AT.put(p.getId(), ticks);
        ModNetwork.sendToServer(new ComboStartPacket(style == ComboStyle.RHALIX_AIR ? hit + ComboStyle.AIR_FLAG : hit, heavy));   // (Rhalix: which of his strings)
        if (style.rhalix()) com.xenowarriors.client.RhalixComboFx.log("start " + style + " hit " + hit + (heavy ? " heavy" : "") + " at " + now
                + " (prev " + lastStyle + " " + lastHit + ")");
        p.swing(InteractionHand.MAIN_HAND);
        p.resetAttackStrengthTicker();
        // stepping in on the hit (Mikiko dashes in his own way, Rhalix rushes in on his)
        LivingEntity t = target(p, style);
        if (style == ComboStyle.RHALIX) { rhalixStep(p, hit, heavy, now, dur, t); return; }
        if (style == ComboStyle.RHALIX_AIR) { airStep(p, hit, t); return; }
        boolean ownDash = ClientCharacterData.get(p) == CharacterType.MIKIKO && (style == ComboStyle.MIKIKO || style == ComboStyle.SWORD);
        if (!ownDash && p.onGround() && t != null && p.distanceTo(t) > 1.6F) {
            Vec3 d = t.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(heavy ? 0.12 : 0.22);
            p.setDeltaMovement(p.getDeltaMovement().add(d));
        }
    }

    // ------------------------------------------------------------------ Rhalix's Shadow Rush

    private static int rushTarget = -1, rushUntil;

    /** Dashing in on them right now (Rhalix's opening hit). */
    public static boolean rushing(LocalPlayer p) { return rushTarget >= 0 && p.tickCount <= rushUntil; }

    /**
     * Rhalix's steps: the string opens with a low dash straight at an enemy 2-5 blocks off (Shadow Rush), skidding to a
     * stop beside them as the claw comes up; he steps into the lunge and the follow-up grab.
     */
    private static void rhalixStep(LocalPlayer p, int hit, boolean heavy, int now, int dur, LivingEntity t) {
        rushTarget = -1;
        if (heavy || !p.onGround() || p.isCrouching()) return;
        if (hit == ComboStyle.LEAP) { leap(p); return; }
        if (hit == ComboStyle.BURST) {                                    // a half step in, to point-blank
            LivingEntity b = t != null ? t : rushPick(p, 4.5);
            if (b != null) { lastTarget = b.getId(); if (p.distanceTo(b) > 1.6) com.xenowarriors.client.mikiko.DashGuard.dash(p, 0.3, 0.0); }
            return;
        }
        if (hit == 0) {
            LivingEntity r = t;
            double reach = 5.5;
            if (r == null || p.distanceTo(r) < 2.0) {
                LivingEntity far = rushPick(p, reach);
                if (far != null && (r == null || p.distanceTo(far) >= 2.0)) r = far;
            }
            if (r != null && p.distanceTo(r) >= 2.0 && p.distanceTo(r) <= reach && Math.abs(r.getY() - p.getY()) < 1.5) {
                rushTarget = r.getId();
                lastTarget = r.getId();
                rushUntil = now + ComboStyle.windup(dur) + 1;
                rushTick(p);
            }
        } else if ((hit == 1 || hit == 5) && t != null && p.distanceTo(t) > 1.3) {
            com.xenowarriors.client.mikiko.DashGuard.dash(p, hit == 5 ? 0.34 : 0.26, 0.0);
        }
    }

    // ------------------------------------------------------------------ Rhalix in the air

    private static int hoverTarget = -1, slamAt = -1;

    /** Someone up in the air near him (for the leap and the air string). */
    private static LivingEntity airPick(LocalPlayer p, double reach) {
        LivingEntity lock = com.xenowarriors.client.LockOn.target();
        if (lock != null && lock.isAlive() && in(p, lock, reach)) return lock;
        if (p.level().getEntity(lastTarget) instanceof LivingEntity l && l.isAlive() && in(p, l, reach)) return l;
        return rushPick(p, reach);
    }

    /** The Rising Leap (attack held fully, on the ground): a jump straight up at someone in the air, or just up. */
    private static void leap(LocalPlayer p) {
        LivingEntity t = airPick(p, 6.5);
        if (p.level().getEntity(lastTarget) instanceof LivingEntity lt)
            com.xenowarriors.client.RhalixComboFx.log(String.format("leap: last target %s %.1f away, %.1f up, on ground %s", lt.getType().getDescriptionId(), p.distanceTo(lt), lt.getY() - p.getY(), lt.onGround()));
        Vec3 v;
        if (t != null && !t.onGround()) {
            Vec3 f = t.position().subtract(p.position()).multiply(1, 0, 1);
            Vec3 spot = t.position().subtract(f.lengthSqr() > 1e-4 ? f.normalize().scale(1.0) : Vec3.ZERO);
            Vec3 to = spot.subtract(p.position());
            double h = Math.max(0.6, to.y + 0.3);
            double vy = Mth.clamp(Math.sqrt(2 * 0.08 * h) + 0.08, 0.45, 1.05);
            double ticksUp = vy / 0.08;
            Vec3 hz = to.multiply(1, 0, 1).scale(1.0 / Math.max(4.0, ticksUp * 0.85));
            if (hz.length() > 0.8) hz = hz.normalize().scale(0.8);
            v = new Vec3(hz.x, vy, hz.z);
            lastTarget = t.getId(); hoverTarget = t.getId();
        } else v = new Vec3(p.getDeltaMovement().x * 0.3, 0.62, p.getDeltaMovement().z * 0.3);
        p.setDeltaMovement(v);
        p.setOnGround(false);
        com.xenowarriors.client.RhalixComboFx.takeOffDust(p);
        com.xenowarriors.client.RhalixComboFx.log(String.format("rising leap: target %s v=(%.2f,%.2f,%.2f)", t == null ? "none" : t.getType().getDescriptionId(), v.x, v.y, v.z));
    }

    /** His air string: the first hit springs him up beside them; he hangs there through the slashes; after the slam he drops. */
    private static void airStep(LocalPlayer p, int hit, LivingEntity t) {
        if (t == null) t = airPick(p, 5.5);
        hoverTarget = t != null ? t.getId() : -1;
        if (t != null) lastTarget = t.getId();
        Vec3 v = p.getDeltaMovement();
        if (hit == 0 && t != null) {
            double up = t.getY() + 0.1 - p.getY();
            Vec3 hz = t.position().subtract(p.position()).multiply(1, 0, 1);
            double d = hz.length();
            hz = d > 1.4 ? hz.normalize().scale(Math.min(0.5, (d - 1.2) * 0.3)) : Vec3.ZERO;
            p.setDeltaMovement(hz.x, Mth.clamp(0.25 + up * 0.18, 0.2, 0.7), hz.z);
        } else if (t != null) p.setDeltaMovement(v.x * 0.5, Math.max(v.y, 0.08), v.z * 0.5);
        slamAt = hit == ComboStyle.RHALIX_AIR.length() - 1 ? p.tickCount + ComboStyle.windup(ComboStyle.RHALIX_AIR.duration(hit, false)) + 1 : -1;
    }

    /** Hanging in the air through his air string (only while there's someone to hit; never once he's landed or flying). */
    private static void airTick(LocalPlayer p) {
        if (cur == null || !cur.style().rhalix() || p.onGround() || p.getAbilities().flying || p.isInWater() || p.isPassenger() || p.isFallFlying()) {
            if (p.onGround()) { hoverTarget = -1; slamAt = -1; }
            return;
        }
        boolean airMove = cur.style() == ComboStyle.RHALIX_AIR || cur.hit() == ComboStyle.LEAP;
        if (!airMove) return;
        if (slamAt >= 0 && p.tickCount >= slamAt) {                         // the slam: he drops after them
            p.setDeltaMovement(p.getDeltaMovement().x * 0.3, -0.95, p.getDeltaMovement().z * 0.3);
            hoverTarget = -1; slamAt = -1;
            return;
        }
        if (!(p.level().getEntity(hoverTarget) instanceof LivingEntity t) || !t.isAlive() || p.distanceTo(t) > 6.5) return;
        if (cur.hit() == ComboStyle.LEAP && p.tickCount - cur.start() < 5) return;   // (still rising)
        Vec3 v = p.getDeltaMovement();
        if (v.y < 0) p.setDeltaMovement(v.x, Math.max(v.y * 0.5, -0.06), v.z);
        // staying just in front of them, a little above
        Vec3 to = t.position().add(0, 0.2, 0).subtract(p.position());
        Vec3 hz = to.multiply(1, 0, 1);
        double d = hz.length();
        if (d > 1.6) { hz = hz.normalize().scale(Math.min(0.2, (d - 1.4) * 0.25)); p.setDeltaMovement(hz.x, p.getDeltaMovement().y, hz.z); }
    }

    /** Who a rush heads for: what you aim at, your lock-on, or the nearest enemy in front of you, within reach. */
    public static LivingEntity rushPick(LocalPlayer p, double reach) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity lock = com.xenowarriors.client.LockOn.target();
        if (lock != null && lock.isAlive() && in(p, lock, reach)) return lock;
        Vec3 look = p.getViewVector(1F).multiply(1, 0, 1).normalize();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1))) {
            if (e == p || !e.isAlive() || !in(p, e, reach)) continue;
            boolean foe = e instanceof Enemy || e.getId() == lastTarget || (e instanceof Mob m && m.getTarget() == p)
                    || e == mc.crosshairPickEntity;
            if (!foe || !com.xenowarriors.client.LockOn.attackable(e)) continue;
            Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
            double d = to.length();
            if (d > 0.3 && to.normalize().dot(look) < 0.8) continue;           // (a narrower cone: it's a long way)
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    /** One tick of the rush: fast and low along the ground, slowing to a skid just short of them. */
    private static void rushTick(LocalPlayer p) {
        if (!(p.level().getEntity(rushTarget) instanceof LivingEntity t) || !t.isAlive()) { rushTarget = -1; return; }
        Vec3 to = t.position().subtract(p.position()).multiply(1, 0, 1);
        double gap = to.length() - (p.getBbWidth() + t.getBbWidth()) / 2.0;
        if (gap < 0.45 || to.lengthSqr() < 1.0E-4) { rushTarget = -1; return; }
        double v = Math.min(1.0, (gap - 0.35) * 0.6);
        Vec3 d = to.normalize();
        p.setDeltaMovement(d.x * v, p.getDeltaMovement().y, d.z * v);
        float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
        p.setYRot(yaw); p.yBodyRot = yaw; p.yHeadRot = yaw;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { cur = null; SWINGS.clear(); STARTED_AT.clear(); holding = false; autoString = false; return; }
        if (ticks % 100 == 0) { SWINGS.keySet().removeIf(id -> mc.level.getEntity(id) == null); STARTED_AT.values().removeIf(t -> ticks - t > 40); }
        holdTick(mc, p);
        airTick(p);
        if (autoString) {                                                   // (after a Kishi Warp: the string plays itself out)
            if (cur != null && cur.style() == ComboStyle.RHALIX && cur.hit() >= 4 && cur.hit() < 7) queued = true;
            else if (cur == null || cur.hit() >= 7 || cur.style() != ComboStyle.RHALIX) autoString = false;
        }
        if (cur == null) { tryStart(p); return; }
        int now = p.tickCount;
        // jump-cancel: jumping out of a ground hit once it's landed - the next click carries on in the air
        if (cur.style() == ComboStyle.RHALIX && !cur.style().special(cur.hit()) && struck && !p.onGround() && p.getDeltaMovement().y > 0.2) {
            lastHit = cur.hit(); lastStyle = cur.style(); lastWasEnder = false; lastEnd = now; cur = null;
            com.xenowarriors.client.RhalixComboFx.log("jump-cancel after hit " + lastHit);
            tryStart(p);
            return;
        }
        if (rushTarget >= 0) { if (now <= rushUntil && cur.style() == ComboStyle.RHALIX && cur.hit() == 0) rushTick(p); else rushTarget = -1; }
        if (!struck && now - cur.start() >= ComboStyle.windup(cur.duration())) {
            struck = true;
            LivingEntity t = target(p, cur.style());
            if (t == null && cur.style().rhalix() && (cur.style().special(cur.hit()) || cur.style() == ComboStyle.RHALIX_AIR))
                t = airPick(p, cur.hit() == ComboStyle.BURST ? 4.3 : 4.8);       // (his burst and his air moves reach a bit further)
            if (t != null) { lastTarget = t.getId(); ModNetwork.sendToServer(new ComboHitPacket(t.getId(), cur.hit(), cur.heavy())); }
        }
        tryStart(p);
        if (cur != null && now - cur.start() >= cur.duration()) {
            lastHit = cur.hit();
            lastStyle = cur.style();
            lastWasEnder = lastWasEnderOf(cur);
            lastEnd = now;
            if (lastWasEnder) lockUntil = now + 4;                              // a breath after a finisher or heavy
            cur = null;
        }
        if (cur == null && !attackDown(mc) && !autoString) queued = queued && now - lastEnd < 6;
    }

    /** Committed to the swing: you can only shuffle while winding up and striking. */
    @SubscribeEvent
    public static void onMove(MovementInputUpdateEvent event) {
        if (com.xenowarriors.client.KishiWarpClient.warping() && event.getEntity() instanceof LocalPlayer) {   // knelt: not going anywhere
            event.getInput().forwardImpulse = 0; event.getInput().leftImpulse = 0; event.getInput().jumping = false;
            return;
        }
        if (cur == null || !(event.getEntity() instanceof LocalPlayer p)) return;
        if (p.tickCount - cur.start() >= ComboStyle.cancel(cur.duration())) return;
        float slow = cur.heavy() ? 0.15F : 0.35F;
        event.getInput().forwardImpulse *= slow;
        event.getInput().leftImpulse *= slow;
    }

    /** The enemy a combo is aimed at right now (what you aim at, or the nearest foe just in front), within 4 blocks. */
    public static LivingEntity assistTarget(LocalPlayer p) {
        return target(p, ComboStyle.SPEAR);
    }

    /** Who the hit lands on: what you aim at, your lock-on, or the nearest enemy right in front of you. */
    private static LivingEntity target(LocalPlayer p, ComboStyle style) {
        Minecraft mc = Minecraft.getInstance();
        double reach = p.getEntityReach() + style.reachBonus;
        if (mc.crosshairPickEntity instanceof LivingEntity l && l != p && l.isAlive() && in(p, l, reach)) return l;
        LivingEntity lock = com.xenowarriors.client.LockOn.target();
        if (lock != null && lock.isAlive() && in(p, lock, reach + 0.5)) return lock;
        Vec3 look = p.getViewVector(1F).multiply(1, 0, 1).normalize();
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1))) {
            if (e == p || !e.isAlive() || !in(p, e, reach + 0.3)) continue;
            boolean foe = e instanceof Enemy || e.getId() == lastTarget || (e instanceof Mob m && m.getTarget() == p);
            if (!foe || !com.xenowarriors.client.LockOn.attackable(e)) continue;
            Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
            double d = to.length();
            if (d > 0.3 && to.normalize().dot(look) < 0.75) continue;          // within ~40 degrees of where you face
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private static boolean in(LocalPlayer p, LivingEntity e, double reach) {
        double r = reach + e.getBbWidth() * 0.5;
        return p.distanceToSqr(e) <= r * r && p.hasLineOfSight(e);
    }
}
