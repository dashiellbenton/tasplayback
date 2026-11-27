package dev.dbenton.tasplayback.core.tools;

import dev.dbenton.tasplayback.core.branch.CanonicalResolver;
import dev.dbenton.tasplayback.core.branch.Segment;
import dev.dbenton.tasplayback.core.event.EventCodec;
import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.format.StreamId;
import dev.dbenton.tasplayback.core.format.StreamReader;
import dev.dbenton.tasplayback.core.format.TaspArchive;
import dev.dbenton.tasplayback.core.frame.FrameCodec;
import dev.dbenton.tasplayback.core.frame.FrameRecord;
import dev.dbenton.tasplayback.core.time.TimeKey;
import dev.dbenton.tasplayback.core.timeline.PlaybackClock;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** {@code java -cp core.jar dev.dbenton.tasplayback.core.tools.Inspect file.tasp} — prints a summary of a recording. */
public final class Inspect {
    private Inspect() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: Inspect <file.tasp | dir.tasp.part>");
            System.exit(2);
        }
        TaspArchive.Recording r = TaspArchive.open(Paths.get(args[0]));
        System.out.println("minecraft " + r.meta.minecraftVersion + " (" + r.meta.loader + "), mod " + r.meta.modVersion
                + ", recorded " + r.meta.recordedAt);
        System.out.println("window " + r.meta.windowWidth + "x" + r.meta.windowHeight + ", gui scale " + r.meta.guiScale
                + ", resource packs " + r.meta.resourcePacks.size());
        for (StreamId s : StreamId.values()) {
            StreamReader.Result res = r.stream(s);
            Map<Integer, Integer> types = new TreeMap<>();
            if (s != StreamId.FRAMES) for (EventRecord e : res.decodeAll(new EventCodec())) types.merge(e.type, 1, Integer::sum);
            System.out.printf("  %-11s %8d records %4d chunks %10d bytes%s%s%n", s.fileName, res.recordCount(), res.chunks.size(),
                    res.totalBytes, types.isEmpty() ? "" : "  types " + types, res.clean() ? "" : "  DAMAGED: " + res.problem);
        }
        for (Segment seg : r.segments.segments()) System.out.println("  " + seg);
        CanonicalResolver.Resolution res = CanonicalResolver.resolve(r.segments);
        List<FrameRecord> frames = res.filter(r.stream(StreamId.FRAMES).decodeAll(new FrameCodec()), f -> f.key);
        List<TimeKey> keys = new ArrayList<>();
        for (FrameRecord f : frames) keys.add(f.key);
        PlaybackClock clock = PlaybackClock.realTime(keys);
        System.out.printf("canonical: %d frames, %.2f s at 20 TPS%s%n", frames.size(), clock.duration(),
                frames.isEmpty() ? "" : ", ticks " + frames.get(0).key.tick + ".." + frames.get(frames.size() - 1).key.tick);
        for (String p : r.problems()) System.out.println("PROBLEM: " + p);
    }
}
