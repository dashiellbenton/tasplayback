package dev.dbenton.tasplayback.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TASPlaybackCoreTest {
    @Test
    void describeMentionsLoaderAndVersion() {
        String s = TASPlaybackCore.describe("fabric", "1.21.11", "0.0.1");
        assertTrue(s.contains("fabric") && s.contains("1.21.11") && s.contains("0.0.1"));
    }
}
