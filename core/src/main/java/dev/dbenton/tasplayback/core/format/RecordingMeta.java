package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.TASPlaybackCore;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contents of {@code meta.json} (PLAN.md §4.2). */
public final class RecordingMeta {
    public static final class ResourcePack {
        public final String id;
        public final String name;
        /** Hex SHA-1 of the pack contents ("" if unknown, e.g. the vanilla built-in pack). */
        public final String sha1;

        public ResourcePack(String id, String name, String sha1) { this.id = id; this.name = name; this.sha1 = sha1; }
    }

    public static final class TickrateChange {
        public final int segment;
        public final long tick;
        public final double ticksPerSecond;
        public final boolean frozen;
        /** What changed it: "vanilla-tick", "tasmod", "lotas", ... */
        public final String source;

        public TickrateChange(int segment, long tick, double ticksPerSecond, boolean frozen, String source) {
            this.segment = segment; this.tick = tick; this.ticksPerSecond = ticksPerSecond; this.frozen = frozen; this.source = source;
        }
    }

    public int formatVersion = TASPlaybackCore.FORMAT_VERSION;
    public String modVersion = "";
    public String minecraftVersion = "";
    /** "fabric", "forge" or "legacyfabric". */
    public String loader = "";
    /** ISO-8601 wall-clock start time (metadata only). */
    public String recordedAt = "";
    public int windowWidth, windowHeight, framebufferWidth, framebufferHeight, guiScale;
    public String language = "en_us";
    public final List<ResourcePack> resourcePacks = new ArrayList<>();
    /** Options that affect rendering (fov, bobView, fovEffectScale, distortionEffectScale, chat settings, volumes, ...). */
    public final Map<String, Object> renderOptions = new LinkedHashMap<>();
    public final List<TickrateChange> tickrateHistory = new ArrayList<>();

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formatVersion", formatVersion);
        m.put("modVersion", modVersion);
        m.put("minecraftVersion", minecraftVersion);
        m.put("loader", loader);
        m.put("recordedAt", recordedAt);
        m.put("crossVersionPlayback", false);
        Map<String, Object> streams = new LinkedHashMap<>();
        for (StreamId s : StreamId.values()) streams.put(s.fileName, s.version);
        m.put("streamVersions", streams);
        Map<String, Object> win = new LinkedHashMap<>();
        win.put("width", windowWidth); win.put("height", windowHeight);
        win.put("framebufferWidth", framebufferWidth); win.put("framebufferHeight", framebufferHeight);
        win.put("guiScale", guiScale);
        m.put("window", win);
        m.put("language", language);
        List<Object> packs = new ArrayList<>();
        for (ResourcePack p : resourcePacks) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.id); pm.put("name", p.name); pm.put("sha1", p.sha1);
            packs.add(pm);
        }
        m.put("resourcePacks", packs);
        m.put("renderOptions", renderOptions);
        List<Object> tr = new ArrayList<>();
        for (TickrateChange t : tickrateHistory) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("segment", t.segment); tm.put("tick", t.tick); tm.put("ticksPerSecond", t.ticksPerSecond);
            tm.put("frozen", t.frozen); tm.put("source", t.source);
            tr.add(tm);
        }
        m.put("tickrateHistory", tr);
        return m;
    }

    public String toJsonString() { return Json.write(toJson()); }

    public static RecordingMeta fromJson(String text) {
        Json.Obj o = new Json.Obj(Json.parseObject(text));
        RecordingMeta m = new RecordingMeta();
        m.formatVersion = o.integer("formatVersion");
        if (m.formatVersion > TASPlaybackCore.FORMAT_VERSION) {
            throw new FormatException("recording format " + m.formatVersion + " is newer than this TASPlayback supports (" + TASPlaybackCore.FORMAT_VERSION + ")");
        }
        m.modVersion = o.str("modVersion", "");
        m.minecraftVersion = o.str("minecraftVersion");
        m.loader = o.str("loader");
        m.recordedAt = o.str("recordedAt", "");
        if (o.has("window")) {
            Json.Obj w = o.obj("window");
            m.windowWidth = w.integer("width", 0); m.windowHeight = w.integer("height", 0);
            m.framebufferWidth = w.integer("framebufferWidth", 0); m.framebufferHeight = w.integer("framebufferHeight", 0);
            m.guiScale = w.integer("guiScale", 0);
        }
        m.language = o.str("language", "en_us");
        for (Json.Obj p : o.objList("resourcePacks")) m.resourcePacks.add(new ResourcePack(p.str("id"), p.str("name", ""), p.str("sha1", "")));
        if (o.has("renderOptions")) m.renderOptions.putAll(o.obj("renderOptions").raw());
        for (Json.Obj t : o.objList("tickrateHistory")) {
            m.tickrateHistory.add(new TickrateChange(t.integer("segment", 0), t.lng("tick"), t.dbl("ticksPerSecond"),
                    t.bool("frozen", false), t.str("source", "unknown")));
        }
        return m;
    }

    /**
     * Differences between the recorded resource packs and the ones active now. Sounds and textures come
     * from the packs, so playback warns on any difference (PLAN.md §4.2).
     */
    public List<String> resourcePackWarnings(List<ResourcePack> current) {
        List<String> w = new ArrayList<>();
        Map<String, ResourcePack> now = new LinkedHashMap<>();
        for (ResourcePack p : current) now.put(p.id, p);
        Map<String, ResourcePack> rec = new LinkedHashMap<>();
        for (ResourcePack p : resourcePacks) rec.put(p.id, p);
        for (ResourcePack p : resourcePacks) {
            ResourcePack c = now.get(p.id);
            if (c == null) w.add("missing resource pack '" + p.id + "' (used when recording)");
            else if (!p.sha1.isEmpty() && !c.sha1.isEmpty() && !p.sha1.equalsIgnoreCase(c.sha1)) {
                w.add("resource pack '" + p.id + "' differs from the recorded version");
            }
        }
        for (ResourcePack p : current) if (!rec.containsKey(p.id)) w.add("extra resource pack '" + p.id + "' (not used when recording)");
        List<String> recOrder = new ArrayList<>(rec.keySet());
        List<String> nowOrder = new ArrayList<>();
        for (String id : now.keySet()) if (rec.containsKey(id)) nowOrder.add(id);
        recOrder.retainAll(now.keySet());
        if (!recOrder.equals(nowOrder)) w.add("resource pack order differs from the recording");
        return w;
    }
}
