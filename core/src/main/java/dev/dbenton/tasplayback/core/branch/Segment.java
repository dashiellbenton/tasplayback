package dev.dbenton.tasplayback.core.branch;

import dev.dbenton.tasplayback.core.time.GameTime;

/**
 * A recording segment. Segment 0 starts the recording; every detected rewind (savestate load,
 * world reload, manual mark) starts a new segment.
 *
 * <p>{@code parent}/{@code link} describe where the segment's starting state came from: the game
 * state at the start of this segment equals the state of {@code parent}'s lineage at game time
 * {@code link}. For a savestate load, {@code parent} is the segment that was live when the state was
 * <em>saved</em> if known, otherwise the segment that was live when it was loaded (the resolver then
 * walks further up to whichever ancestor actually contains {@code link}).
 */
public final class Segment {
    public final int id;
    /** Game time of this segment's first frame. */
    public final GameTime start;
    /** Frame counter value of this segment's first frame. */
    public final long startFrame;
    /** Parent segment id, or -1 for a root segment. */
    public final int parent;
    /** Branch point in the parent's lineage; {@code null} for root segments. */
    public final GameTime link;
    /** Why the segment started: "start", "savestate-load", "world-reload", "tickrate-change", "manual-rewind", ... */
    public final String cause;
    /** What detected it: "vanilla", "tasmod", "lotas", "manual", ... */
    public final String detectedBy;
    /** User override: treat this boundary as a false positive (keep all parent frames before this segment). */
    public final boolean ignored;

    public Segment(int id, GameTime start, long startFrame, int parent, GameTime link, String cause, String detectedBy, boolean ignored) {
        if (id < 0) throw new IllegalArgumentException("segment id must be >= 0");
        if (parent >= id) throw new IllegalArgumentException("parent " + parent + " must precede segment " + id);
        if (parent < 0 && link != null) throw new IllegalArgumentException("root segment cannot have a link point");
        if (parent >= 0 && link == null) throw new IllegalArgumentException("child segment needs a link point");
        this.id = id;
        this.start = start;
        this.startFrame = startFrame;
        this.parent = parent;
        this.link = link;
        this.cause = cause;
        this.detectedBy = detectedBy;
        this.ignored = ignored;
    }

    public static Segment root(int id, GameTime start, long startFrame) {
        return new Segment(id, start, startFrame, -1, null, "start", "recorder", false);
    }

    public static Segment rewind(int id, GameTime start, long startFrame, int parent, GameTime link, String cause, String detectedBy) {
        return new Segment(id, start, startFrame, parent, link, cause, detectedBy, false);
    }

    public Segment withIgnored(boolean ignored) {
        return new Segment(id, start, startFrame, parent, link, cause, detectedBy, ignored);
    }

    public Segment withLink(int parent, GameTime link) {
        return new Segment(id, start, startFrame, parent, link, cause, detectedBy, ignored);
    }

    public boolean isRoot() { return parent < 0; }

    @Override public String toString() {
        return "Segment{" + id + " start=" + start + " parent=" + parent + " link=" + link + " cause=" + cause + (ignored ? " IGNORED" : "") + "}";
    }
}
