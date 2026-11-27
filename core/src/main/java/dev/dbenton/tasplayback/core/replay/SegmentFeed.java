package dev.dbenton.tasplayback.core.replay;

import dev.dbenton.tasplayback.core.event.EventRecord;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.time.TimeKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Replays one segment's connection: decides which recorded inbound frames to feed into the replay
 * connection, and when.
 *
 * <p>The replay driver advances a tick counter and a recorded-frame position. Before running client
 * tick {@code n+1} (while the counter is {@code n}) and before showing recorded frame {@code f}, it
 * calls {@link #framesDue(long, long)}: every DELIVER recorded at tick {@code <= n} and frame {@code <= f}
 * is due, and the frames up to the highest due sequence number are returned in order, exactly once.
 */
public final class SegmentFeed {
    private final List<byte[]> frames;
    private final List<TimeKey> deliverKeys;
    private final long[] deliverSeq;
    private int nextDeliver;
    private int fed; // frames [0, fed) have been handed out

    /**
     * @param segmentEvents WORLD events of one segment, in recording order
     * @param keepDeliver   which DELIVER keys belong to the canonical timeline (e.g. {@code resolution::isKept});
     *                      deliveries after the segment's branch point are dropped
     */
    public SegmentFeed(List<EventRecord> segmentEvents, Predicate<TimeKey> keepDeliver) {
        List<byte[]> f = new ArrayList<>();
        List<TimeKey> dk = new ArrayList<>();
        List<Long> ds = new ArrayList<>();
        for (EventRecord e : segmentEvents) {
            if (e.type == WorldEvents.RAW_FRAME) {
                long seq = WorldEvents.seqOf(e);
                if (seq != f.size()) throw new FormatException("raw frame sequence gap: got " + seq + ", expected " + f.size());
                f.add(WorldEvents.frameBytes(e));
            } else if (e.type == WorldEvents.DELIVER) {
                if (keepDeliver.test(e.key)) {
                    dk.add(e.key);
                    ds.add(WorldEvents.seqOf(e));
                }
            }
        }
        this.frames = Collections.unmodifiableList(f);
        this.deliverKeys = dk;
        this.deliverSeq = new long[ds.size()];
        for (int i = 0; i < deliverSeq.length; i++) deliverSeq[i] = ds.get(i);
    }

    public int frameCount() { return frames.size(); }
    public int deliverCount() { return deliverSeq.length; }
    public boolean exhausted() { return nextDeliver >= deliverSeq.length; }

    /** Key of the next pending DELIVER, or null when none is left. */
    public TimeKey nextDeliverKey() { return exhausted() ? null : deliverKeys.get(nextDeliver); }

    /** Frames to feed now, given the replay's tick counter and recorded-frame position. */
    public List<byte[]> framesDue(long tickCount, long frame) {
        long upTo = -1;
        while (nextDeliver < deliverSeq.length) {
            TimeKey k = deliverKeys.get(nextDeliver);
            if (k.tick > tickCount || k.frame > frame) break;
            upTo = Math.max(upTo, deliverSeq[nextDeliver]);
            nextDeliver++;
        }
        if (upTo < fed) return Collections.emptyList();
        if (upTo >= frames.size()) throw new FormatException("DELIVER refers to frame " + upTo + " but only " + frames.size() + " were recorded");
        List<byte[]> out = new ArrayList<>(frames.subList(fed, (int) upTo + 1));
        fed = (int) upTo + 1;
        return out;
    }
}
