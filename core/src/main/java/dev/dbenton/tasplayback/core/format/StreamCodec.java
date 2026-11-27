package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.io.ByteSink;
import dev.dbenton.tasplayback.core.io.ByteSource;
import dev.dbenton.tasplayback.core.time.TimeKey;

/**
 * Encodes/decodes records of one stream. Instances are stateful (delta coding) and single-direction:
 * use one instance per writer and one per reader. {@link #beginChunk()} resets the delta state, so every
 * chunk is independently decodable (a keyframe), which is what makes recovery and seeking possible.
 */
public interface StreamCodec<T> {
    void beginChunk();

    void encode(T record, ByteSink out);

    T decode(ByteSource in);

    TimeKey keyOf(T record);
}
