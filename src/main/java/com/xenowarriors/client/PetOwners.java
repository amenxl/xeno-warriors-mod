package com.xenowarriors.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Who owns which pet (as the server tells us). */
public final class PetOwners {
    private static final Map<Integer, UUID> OWNERS = new HashMap<>();

    private PetOwners() {}

    public static void set(int id, UUID owner) {
        if (OWNERS.size() > 2048) OWNERS.clear();
        OWNERS.put(id, owner);
    }

    public static UUID owner(int id) {
        return OWNERS.get(id);
    }
}
