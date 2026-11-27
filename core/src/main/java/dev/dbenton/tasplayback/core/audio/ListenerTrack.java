package dev.dbenton.tasplayback.core.audio;

/** Listener pose as a function of output sample position (usually built from the recorded camera frames). */
public interface ListenerTrack {
    ListenerPose poseAt(long sample);

    static ListenerTrack fixed(ListenerPose pose) { return sample -> pose; }
}
