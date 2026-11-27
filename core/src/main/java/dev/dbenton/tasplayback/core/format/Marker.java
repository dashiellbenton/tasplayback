package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.json.Json;
import dev.dbenton.tasplayback.core.time.TimeKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A user marker or chapter ({@code markers.json}). */
public final class Marker {
    public final TimeKey key;
    public final String label;
    /** "marker" or "chapter". */
    public final String kind;

    public Marker(TimeKey key, String label, String kind) { this.key = key; this.label = label; this.kind = kind; }

    public static String toJson(List<Marker> markers) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", 1);
        List<Object> list = new ArrayList<>();
        for (Marker mk : markers) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("segment", mk.key.segment); m.put("tick", mk.key.tick); m.put("partial", (double) mk.key.partial);
            m.put("frame", mk.key.frame); m.put("label", mk.label); m.put("kind", mk.kind);
            list.add(m);
        }
        root.put("markers", list);
        return Json.write(root);
    }

    public static List<Marker> fromJson(String text) {
        Json.Obj root = new Json.Obj(Json.parseObject(text));
        List<Marker> out = new ArrayList<>();
        for (Json.Obj m : root.objList("markers")) {
            try {
                out.add(new Marker(new TimeKey(m.integer("segment"), m.lng("tick"), (float) m.dbl("partial", 0), m.lng("frame")),
                        m.str("label", ""), m.str("kind", "marker")));
            } catch (IllegalArgumentException e) {
                throw new dev.dbenton.tasplayback.core.io.FormatException("markers.json: " + e.getMessage());
            }
        }
        return out;
    }
}
