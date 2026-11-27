package dev.dbenton.tasplayback.core.branch;

import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.json.Json;
import dev.dbenton.tasplayback.core.time.GameTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All segments of a recording plus user overrides; persisted as {@code segments.json}. */
public final class SegmentGraph {
    public static final int FORMAT_VERSION = 1;

    private final List<Segment> segments = new ArrayList<>();
    /** User override: resolve the canonical timeline ending in this segment instead of the last one. -1 = none. */
    private int finalOverride = -1;

    public List<Segment> segments() { return Collections.unmodifiableList(segments); }

    public void add(Segment s) {
        if (s.id != segments.size()) throw new IllegalArgumentException("segments must be added in id order; expected id " + segments.size());
        segments.add(s);
    }

    public Segment get(int id) {
        if (id < 0 || id >= segments.size()) throw new IllegalArgumentException("no segment " + id);
        return segments.get(id);
    }

    public int size() { return segments.size(); }

    public void replace(Segment s) { get(s.id); segments.set(s.id, s); }

    public int finalOverride() { return finalOverride; }

    public void setFinalOverride(int id) {
        if (id >= 0) get(id);
        finalOverride = id;
    }

    /** The segment the canonical timeline ends in. */
    public int finalSegment() {
        if (segments.isEmpty()) throw new IllegalStateException("no segments");
        return finalOverride >= 0 ? finalOverride : segments.size() - 1;
    }

    // ------------------------------------------------------------------ JSON

    public Map<String, Object> toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", FORMAT_VERSION);
        if (finalOverride >= 0) root.put("finalSegmentOverride", finalOverride);
        List<Object> list = new ArrayList<>();
        for (Segment s : segments) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id);
            m.put("startTick", s.start.tick);
            m.put("startPartial", (double) s.start.partial);
            m.put("startFrame", s.startFrame);
            if (!s.isRoot()) {
                m.put("parent", s.parent);
                m.put("linkTick", s.link.tick);
                m.put("linkPartial", (double) s.link.partial);
            }
            m.put("cause", s.cause);
            m.put("detectedBy", s.detectedBy);
            if (s.ignored) m.put("ignored", true);
            list.add(m);
        }
        root.put("segments", list);
        return root;
    }

    public String toJsonString() { return Json.write(toJson()); }

    public static SegmentGraph fromJson(String text) {
        Json.Obj root = new Json.Obj(Json.parseObject(text));
        int v = root.integer("formatVersion");
        if (v > FORMAT_VERSION) throw new FormatException("segments.json version " + v + " is newer than supported " + FORMAT_VERSION);
        SegmentGraph g = new SegmentGraph();
        for (Json.Obj o : root.objList("segments")) {
            try {
                int id = o.integer("id");
                GameTime start = new GameTime(o.lng("startTick"), (float) o.dbl("startPartial", 0));
                long startFrame = o.lng("startFrame", 0);
                int parent = o.integer("parent", -1);
                GameTime link = parent >= 0 ? new GameTime(o.lng("linkTick"), (float) o.dbl("linkPartial", 0)) : null;
                g.add(new Segment(id, start, startFrame, parent, link, o.str("cause", "unknown"), o.str("detectedBy", "unknown"), o.bool("ignored", false)));
            } catch (IllegalArgumentException e) {
                throw new FormatException("segments.json: " + e.getMessage());
            }
        }
        int fo = root.integer("finalSegmentOverride", -1);
        if (fo >= g.size()) throw new FormatException("segments.json: finalSegmentOverride " + fo + " does not exist");
        g.finalOverride = fo;
        return g;
    }
}
