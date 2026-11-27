package dev.dbenton.tasplayback.fabric.mc12111;

import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.replay.GuiEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** F3 overlay text: recorded per frame while visible (only when it changed), replayed verbatim. */
public final class DebugText {
    private static final List<List<String>> lastRecorded = Arrays.asList(new ArrayList<String>(), new ArrayList<String>());
    private static final List<List<String>> replayLines = Arrays.asList(new ArrayList<String>(), new ArrayList<String>());

    private DebugText() {}

    static void reset() {
        for (List<String> l : lastRecorded) l.clear();
        for (List<String> l : replayLines) l.clear();
    }

    public static void onLines(List<String> lines, boolean left) {
        TaspClient c = TaspClient.get();
        int side = left ? 0 : 1;
        if (c.isReplaying()) {
            lines.clear();
            lines.addAll(replayLines.get(side));
            return;
        }
        if (!c.isRecording() || lines.equals(lastRecorded.get(side))) return;
        lastRecorded.get(side).clear();
        lastRecorded.get(side).addAll(lines);
        ByteSink s = new ByteSink(256);
        s.writeBoolean(left).writeVarInt(lines.size());
        for (String l : lines) s.writeString(l == null ? "" : l);
        Tripwire.enter();
        try {
            c.onGuiEvent(GuiEvents.DEBUG_TEXT, s.toByteArray());
        } finally {
            Tripwire.exit();
        }
    }

    static void apply(byte[] payload) {
        ByteSource in = new ByteSource(payload);
        boolean left = in.readBoolean();
        int n = in.readVarInt();
        List<String> l = replayLines.get(left ? 0 : 1);
        l.clear();
        for (int i = 0; i < n; i++) l.add(in.readString());
    }
}
