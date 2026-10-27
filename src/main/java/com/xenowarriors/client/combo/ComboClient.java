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
    private static final Map<Integer, Swing> SWINGS = new HashMap<>();
    private static final Map<Integer, Integer> STARTED_AT = new HashMap<>();   // id -> client tick a combo swing began

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
        queued = true;
        queuedHeavy = mc.player.isShiftKeyDown();
        tryStart(mc.player);
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
        ComboStyle style = styleFor(p);
        if (style == null) { queued = false; return; }
        boolean heavy = queuedHeavy;
        int hit;
        if (cur != null && cur.style() == style && !lastWasEnderOf(cur)) hit = cur.hit() + 1;
        else if (cur == null && now - lastEnd <= ComboStyle.CHAIN && lastHit >= 0 && !lastWasEnder && lastHit + 1 < style.length()) hit = lastHit + 1;
        else hit = 0;
        if (hit >= style.length()) hit = 0;
        queued = false;
        start(p, style, hit, heavy, now);
    }

    private static boolean lastWasEnderOf(Swing s) {
        return s.heavy() || s.hit() >= s.style().length() - 1;
    }

    private static void start(LocalPlayer p, ComboStyle style, int hit, boolean heavy, int now) {
        int dur = style.duration(hit, heavy);
        cur = new Swing(style, hit, heavy, now, dur);
        struck = false;
        SWINGS.put(p.getId(), cur);
        STARTED_AT.put(p.getId(), ticks);
        ModNetwork.sendToServer(new ComboStartPacket(hit, heavy));
        p.swing(InteractionHand.MAIN_HAND);
        p.resetAttackStrengthTicker();
        // stepping in on the hit (Mikiko dashes in his own way)
        LivingEntity t = target(p, style);
        boolean ownDash = ClientCharacterData.get(p) == CharacterType.MIKIKO && (style == ComboStyle.MIKIKO || style == ComboStyle.SWORD);
        if (!ownDash && p.onGround() && t != null && p.distanceTo(t) > 1.6F) {
            Vec3 d = t.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(heavy ? 0.12 : 0.22);
            p.setDeltaMovement(p.getDeltaMovement().add(d));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { cur = null; SWINGS.clear(); STARTED_AT.clear(); return; }
        if (ticks % 100 == 0) { SWINGS.keySet().removeIf(id -> mc.level.getEntity(id) == null); STARTED_AT.values().removeIf(t -> ticks - t > 40); }
        if (cur == null) { tryStart(p); return; }
        int now = p.tickCount;
        if (!struck && now - cur.start() >= ComboStyle.windup(cur.duration())) {
            struck = true;
            LivingEntity t = target(p, cur.style());
            if (t != null) { lastTarget = t.getId(); ModNetwork.sendToServer(new ComboHitPacket(t.getId(), cur.hit(), cur.heavy())); }
        }
        tryStart(p);
        if (cur != null && now - cur.start() >= cur.duration()) {
            lastHit = cur.hit();
            lastWasEnder = lastWasEnderOf(cur);
            lastEnd = now;
            if (lastWasEnder) lockUntil = now + 4;                              // a breath after a finisher or heavy
            cur = null;
        }
        if (cur == null && !mc.options.keyAttack.isDown()) queued = queued && now - lastEnd < 6;
    }

    /** Committed to the swing: you can only shuffle while winding up and striking. */
    @SubscribeEvent
    public static void onMove(MovementInputUpdateEvent event) {
        if (cur == null || !(event.getEntity() instanceof LocalPlayer p)) return;
        if (p.tickCount - cur.start() >= ComboStyle.cancel(cur.duration())) return;
        float slow = cur.heavy() ? 0.15F : 0.35F;
        event.getInput().forwardImpulse *= slow;
        event.getInput().leftImpulse *= slow;
    }

    /** Who the hit lands on: what you aim at, your lock-on, or the nearest enemy right in front of you. */
    private static LivingEntity target(LocalPlayer p, ComboStyle style) {
        Minecraft mc = Minecraft.getInstance();
        double reach = p.getEntityReach() + style.reachBonus;
        if (mc.crosshairPickEntity instanceof LivingEntity l && l.isAlive() && in(p, l, reach)) return l;
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
