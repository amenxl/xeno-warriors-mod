package com.xenowarriors.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xenowarriors.XenoWarriors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.Reader;
import java.util.*;

/**
 * Story Mode's content, read from data/xenowarriors/story/*.json (one file per storyline, plus sides.json): missions made
 * of steps (scenes of dialogue, fights, survive-for-a-while), each line with variants for Skylander / not and for the size
 * of the party. Reloaded with /reload, so missions can be changed without touching code.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StoryDefs {
    private StoryDefs() {}

    /** The storylines in the order they unlock: the first two either way round, then SR and Government either way, then the finale. */
    public static final List<String> ORDER = List.of("rhalix", "mikiko", "sr", "gov", "finale");

    public record Choice(String text, String set, List<Line> reply) {}

    /** One line: who says it (s: a character id, "you", "narrator" or another speaker), shown name (n), text and its variants. */
    public record Line(String s, String n, String t, Map<String, String> variants, String ifFlag, String unlessFlag, List<Choice> choice) {
        /** The text for this audience: party size (1 solo, 2 duo, 3 trio, 4 full; "group" for 2+), Skylander ("sky") or not ("ground"). */
        public String text(int size, boolean sky) {
            String sk = sky ? "sky" : "ground", sz = size >= 4 ? "full" : size == 3 ? "trio" : size == 2 ? "duo" : "solo";
            for (String k : new String[]{sz + "_" + sk, sz, size > 1 ? "group_" + sk : null, size > 1 ? "group" : null, sk}) {
                if (k != null && variants.containsKey(k)) return variants.get(k);
            }
            return t == null ? "" : t;
        }
    }

    public record Spawn(String e, int n, boolean boss, String name, float escape, boolean captain) {}

    public record Step(String type, String place, String obj, List<String> npcs, List<String> meet, List<Line> lines, List<List<Spawn>> waves,
                       List<String> allies, List<Line> escapeLines, int seconds, String ifFlag, Map<String, Integer> skyBond) {}

    public record Mission(String id, String title, String desc, int karma, int lvl, boolean extra, List<Step> steps, int money, Map<String, Integer> bond) {}

    public record Path(String id, String title, String hub, String unlock, List<String> npcs, List<Mission> missions) {
        public int indexOf(String missionId) { for (int i = 0; i < missions.size(); i++) if (missions.get(i).id().equals(missionId)) return i; return -1; }
    }

    public record Side(String ch, String title, String e, int n, int bond, List<Line> start, List<Line> end) {}

    public static final Map<String, Path> PATHS = new LinkedHashMap<>();
    public static final Map<String, Side> SIDES = new LinkedHashMap<>();

    public static Path path(String id) { return id == null ? null : PATHS.get(id); }

    // ------------------------------------------------------------------ reading

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
    private static int num(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }
    private static List<String> strs(JsonObject o, String k) {
        List<String> l = new ArrayList<>();
        if (o.has(k)) for (JsonElement e : o.getAsJsonArray(k)) l.add(e.getAsString());
        return l;
    }
    private static Map<String, Integer> ints(JsonObject o, String k) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (o.has(k)) for (var e : o.getAsJsonObject(k).entrySet()) m.put(e.getKey(), e.getValue().getAsInt());
        return m;
    }

    private static final Set<String> NOT_VARIANTS = Set.of("s", "n", "t", "if", "unless", "choice");

    static List<Line> lines(JsonArray a) {
        List<Line> l = new ArrayList<>();
        if (a == null) return l;
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            Map<String, String> v = new HashMap<>();
            for (var en : o.entrySet()) if (!NOT_VARIANTS.contains(en.getKey()) && en.getValue().isJsonPrimitive()) v.put(en.getKey(), en.getValue().getAsString());
            List<Choice> ch = new ArrayList<>();
            if (o.has("choice")) for (JsonElement c : o.getAsJsonArray("choice")) {
                JsonObject co = c.getAsJsonObject();
                ch.add(new Choice(str(co, "text"), str(co, "set"), lines(co.has("reply") ? co.getAsJsonArray("reply") : null)));
            }
            l.add(new Line(str(o, "s"), str(o, "n"), str(o, "t"), v, str(o, "if"), str(o, "unless"), ch));
        }
        return l;
    }

    static Path readPath(JsonObject o) {
        List<Mission> ms = new ArrayList<>();
        for (JsonElement me : o.getAsJsonArray("missions")) {
            JsonObject m = me.getAsJsonObject();
            List<Step> steps = new ArrayList<>();
            for (JsonElement se : m.getAsJsonArray("steps")) {
                JsonObject s = se.getAsJsonObject();
                List<List<Spawn>> waves = new ArrayList<>();
                if (s.has("waves")) for (JsonElement we : s.getAsJsonArray("waves")) {
                    List<Spawn> w = new ArrayList<>();
                    for (JsonElement pe : we.getAsJsonArray()) {
                        JsonObject p = pe.getAsJsonObject();
                        w.add(new Spawn(str(p, "e"), num(p, "n", 1), p.has("boss") && p.get("boss").getAsBoolean(), str(p, "name"),
                                p.has("escape") ? p.get("escape").getAsFloat() : 0F, p.has("captain") && p.get("captain").getAsBoolean()));
                    }
                    waves.add(w);
                }
                steps.add(new Step(str(s, "type"), str(s, "place"), str(s, "obj"), strs(s, "npcs"), strs(s, "meet"),
                        lines(s.has("lines") ? s.getAsJsonArray("lines") : null), waves, strs(s, "allies"),
                        lines(s.has("escape_lines") ? s.getAsJsonArray("escape_lines") : null), num(s, "seconds", 60), str(s, "if"), ints(s, "skyBond")));
            }
            JsonObject r = m.has("reward") ? m.getAsJsonObject("reward") : new JsonObject();
            ms.add(new Mission(str(m, "id"), str(m, "title"), str(m, "desc"), num(m, "karma", 0), num(m, "lvl", 1),
                    m.has("extra") && m.get("extra").getAsBoolean(), steps, num(r, "money", 0), ints(r, "bond")));
        }
        return new Path(str(o, "path"), str(o, "title"), str(o, "hub"), str(o, "unlock"), strs(o, "npcs"), ms);
    }

    /** (Tests and the dedicated reload) reads everything from a resource manager. */
    public static void load(ResourceManager rm) {
        Map<String, Path> paths = new LinkedHashMap<>();
        Map<String, Side> sides = new LinkedHashMap<>();
        for (var e : rm.listResources("story", rl -> rl.getPath().endsWith(".json")).entrySet()) {
            if (!e.getKey().getNamespace().equals(XenoWarriors.MOD_ID)) continue;
            try (Reader r = e.getValue().openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                if (o.has("sides")) {
                    for (JsonElement se : o.getAsJsonArray("sides")) {
                        JsonObject s = se.getAsJsonObject();
                        sides.put(str(s, "char"), new Side(str(s, "char"), str(s, "title"), str(s, "e"), num(s, "n", 6), num(s, "bond", 8),
                                lines(s.getAsJsonArray("start")), lines(s.getAsJsonArray("end"))));
                    }
                } else {
                    Path p = readPath(o);
                    paths.put(p.id(), p);
                }
            } catch (Exception x) {
                XenoWarriors.LOGGER.error("Story: couldn't read {}", e.getKey(), x);
            }
        }
        PATHS.clear();
        for (String id : ORDER) if (paths.containsKey(id)) PATHS.put(id, paths.get(id));
        for (var en : paths.entrySet()) PATHS.putIfAbsent(en.getKey(), en.getValue());
        SIDES.clear(); SIDES.putAll(sides);
        XenoWarriors.LOGGER.info("Story: {} storylines, {} missions, {} side missions", PATHS.size(),
                PATHS.values().stream().mapToInt(p -> p.missions().size()).sum(), SIDES.size());
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent e) {
        e.addListener(new SimplePreparableReloadListener<ResourceManager>() {
            @Override protected ResourceManager prepare(ResourceManager rm, ProfilerFiller pf) { return rm; }
            @Override protected void apply(ResourceManager rm, ResourceManager rm2, ProfilerFiller pf) { load(rm); }
        });
    }

    @SuppressWarnings("unused")
    private static ResourceLocation id(String p) { return XenoWarriors.id(p); }
}
