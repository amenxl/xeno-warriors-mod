package com.xenowarriors.compat.epicfight;

import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** (Epic Fight side of foes/FoeAnims) playing a foe's moves. */
public final class EfFoes {
    private EfFoes() {}

    private static final java.util.Map<LivingEntity, Integer> COMBO = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<LivingEntity, Long> LAST = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean swing(LivingEntity e, String[] keys) {
        LivingEntityPatch patch = EpicFightCapabilities.getEntityPatch(e, LivingEntityPatch.class);
        if (patch == null) return false;
        java.util.List moves = new java.util.ArrayList();
        if (keys != null) {
            for (String k : keys) { var a = acc(k); if (a != null) moves.add(a); }
        } else {
            try {
                var cap = patch.getHoldingItemCapability(net.minecraft.world.InteractionHand.MAIN_HAND);
                var list = cap == null ? null : cap.getAutoAttackMotion(null);
                if (list != null && list.size() > 2) moves.addAll(list.subList(0, list.size() - 2));
            } catch (Throwable ignored) {}
        }
        if (moves.isEmpty()) return false;
        long now = e.level().getGameTime();
        Long last = LAST.put(e, now);
        int i = last != null && now - last < 40 ? COMBO.getOrDefault(e, -1) + 1 : 0;       // a pause starts the combo over
        COMBO.put(e, i);
        patch.playAnimationSynchronized((AnimationManager.AnimationAccessor) moves.get(i % moves.size()), 0F);
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean play(LivingEntity e, String key) {
        LivingEntityPatch patch = EpicFightCapabilities.getEntityPatch(e, LivingEntityPatch.class);
        var a = acc(key);
        if (patch == null || a == null) return false;
        patch.playAnimationSynchronized((AnimationManager.AnimationAccessor) a, 0F);
        return true;
    }

    private static final java.util.Map<String, Object> CACHE = new java.util.HashMap<>();
    private static Object acc(String key) {
        return CACHE.computeIfAbsent(key, k -> {
            try { return AnimationManager.byKey(k); } catch (Throwable t) { return null; }
        });
    }
}
