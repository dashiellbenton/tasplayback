package dev.dbenton.tasplayback.core.io;

/** ZigZag helpers for signed varints. */
public final class VarInts {
    private VarInts() {}
    public static long zigZag(long v) { return (v << 1) ^ (v >> 63); }
    public static int zigZag(int v) { return (v << 1) ^ (v >> 31); }
    public static long unZigZag(long v) { return (v >>> 1) ^ -(v & 1); }
}
