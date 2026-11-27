package dev.dbenton.tasplayback.core.format;

import dev.dbenton.tasplayback.core.branch.SegmentGraph;
import dev.dbenton.tasplayback.core.io.FormatException;
import dev.dbenton.tasplayback.core.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The {@code .tasp} container: a zip holding the JSON files and stream files of a recording
 * (PLAN.md §4.2). Stream files are stored uncompressed in the zip because their chunks are already
 * deflated.
 */
public final class TaspArchive {
    private TaspArchive() {}

    public static final String EXTENSION = ".tasp";
    public static final String PART_SUFFIX = ".tasp.part";

    /** A recording loaded from a {@code .tasp} file or a {@code .tasp.part} directory. */
    public static final class Recording {
        public final RecordingMeta meta;
        public final SegmentGraph segments;
        public final List<Marker> markers;
        public final Map<StreamId, StreamReader.Result> streams;
        /** True if the source was a part directory whose status was not "complete" (crashed/unfinished recording). */
        public final boolean unfinished;

        Recording(RecordingMeta meta, SegmentGraph segments, List<Marker> markers, Map<StreamId, StreamReader.Result> streams, boolean unfinished) {
            this.meta = meta;
            this.segments = segments;
            this.markers = Collections.unmodifiableList(markers);
            this.streams = Collections.unmodifiableMap(streams);
            this.unfinished = unfinished;
        }

        public StreamReader.Result stream(StreamId id) { return streams.get(id); }

        /** Human-readable problems found while reading (damaged stream tails etc.). Empty when clean. */
        public List<String> problems() {
            List<String> p = new ArrayList<>();
            if (unfinished) p.add("recording was not finished cleanly (crash or forced exit); recovered up to the last complete chunk");
            for (StreamReader.Result r : streams.values()) {
                if (!r.clean()) p.add(r.stream.fileName + ": " + r.problem + " (" + (r.totalBytes - r.validBytes) + " bytes discarded)");
            }
            return p;
        }
    }

    // ------------------------------------------------------------------ reading

    /** Opens a {@code .tasp} file or a {@code .tasp.part} directory. Damaged stream tails are tolerated. */
    public static Recording open(Path path) throws IOException {
        if (Files.isDirectory(path)) return openDirectory(path);
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Map<String, byte[]> files = new LinkedHashMap<>();
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                try (InputStream in = zip.getInputStream(e)) { files.put(e.getName(), readAll(in)); }
            }
            return fromFiles(files, false);
        }
    }

    private static Recording openDirectory(Path dir) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : allFileNames()) {
            Path p = dir.resolve(name);
            if (Files.isRegularFile(p)) files.put(name, Files.readAllBytes(p));
        }
        boolean unfinished = true;
        byte[] status = files.get(AsyncRecorder.STATUS_FILE);
        if (status != null) {
            try {
                Json.Obj o = new Json.Obj(Json.parseObject(new String(status, StandardCharsets.UTF_8)));
                unfinished = !"complete".equals(o.str("state", ""));
            } catch (FormatException ignored) { /* corrupt status: treat as unfinished */ }
        }
        return fromFiles(files, unfinished);
    }

    private static List<String> allFileNames() {
        List<String> names = new ArrayList<>();
        names.add(AsyncRecorder.META_FILE);
        names.add(AsyncRecorder.SEGMENTS_FILE);
        names.add(AsyncRecorder.MARKERS_FILE);
        names.add(AsyncRecorder.STATUS_FILE);
        for (StreamId s : StreamId.values()) names.add(s.fileName);
        return names;
    }

    private static Recording fromFiles(Map<String, byte[]> files, boolean unfinished) {
        byte[] meta = files.get(AsyncRecorder.META_FILE);
        if (meta == null) throw new FormatException("recording has no meta.json");
        RecordingMeta m = RecordingMeta.fromJson(new String(meta, StandardCharsets.UTF_8));
        byte[] seg = files.get(AsyncRecorder.SEGMENTS_FILE);
        if (seg == null) throw new FormatException("recording has no segments.json");
        SegmentGraph g = SegmentGraph.fromJson(new String(seg, StandardCharsets.UTF_8));
        byte[] mk = files.get(AsyncRecorder.MARKERS_FILE);
        List<Marker> markers = mk == null ? new ArrayList<Marker>() : Marker.fromJson(new String(mk, StandardCharsets.UTF_8));
        Map<StreamId, StreamReader.Result> streams = new EnumMap<>(StreamId.class);
        for (StreamId s : StreamId.values()) {
            byte[] data = files.get(s.fileName);
            if (data == null) data = ChunkFormat.fileHeader(s, s.version); // absent stream = empty
            streams.put(s, StreamReader.read(data, s, s.version));
        }
        return new Recording(m, g, markers, streams, unfinished);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ writing

    /**
     * Packs a part directory into a {@code .tasp} file. Stream files are copied only up to their last
     * valid chunk, so this also serves as crash recovery for an unfinished part directory. The output is
     * written to a temporary file and moved into place, so a failure never leaves a half-written archive.
     *
     * @return the problems found while packing (empty for a clean, finished recording)
     */
    public static List<String> pack(Path partDir, Path taspFile) throws IOException {
        Recording rec = openDirectory(partDir);
        Path tmp = taspFile.resolveSibling(taspFile.getFileName() + ".tmp");
        try (OutputStream fo = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(fo)) {
            zip.setLevel(9);
            putDeflated(zip, AsyncRecorder.META_FILE, Files.readAllBytes(partDir.resolve(AsyncRecorder.META_FILE)));
            putDeflated(zip, AsyncRecorder.SEGMENTS_FILE, rec.segments.toJsonString().getBytes(StandardCharsets.UTF_8));
            putDeflated(zip, AsyncRecorder.MARKERS_FILE, Marker.toJson(rec.markers).getBytes(StandardCharsets.UTF_8));
            for (StreamId s : StreamId.values()) {
                Path p = partDir.resolve(s.fileName);
                if (!Files.isRegularFile(p)) continue;
                byte[] data = Files.readAllBytes(p);
                StreamReader.Result r = rec.stream(s);
                int valid = (int) Math.min(data.length, r.validBytes);
                putStored(zip, s.fileName, data, valid);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        try {
            Files.move(tmp, taspFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, taspFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return rec.problems();
    }

    private static void putDeflated(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.DEFLATED);
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }

    private static void putStored(ZipOutputStream zip, String name, byte[] data, int len) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        CRC32 crc = new CRC32();
        crc.update(data, 0, len);
        e.setCrc(crc.getValue());
        e.setSize(len);
        e.setCompressedSize(len);
        zip.putNextEntry(e);
        zip.write(data, 0, len);
        zip.closeEntry();
    }

    /** Deletes a part directory after a successful {@link #pack}. Only deletes files the recorder creates. */
    public static void deletePartDirectory(Path partDir) throws IOException {
        if (!partDir.getFileName().toString().endsWith(PART_SUFFIX)) throw new IOException("not a part directory: " + partDir);
        for (String name : allFileNames()) {
            Files.deleteIfExists(partDir.resolve(name));
            Files.deleteIfExists(partDir.resolve(name + ".tmp"));
        }
        Files.delete(partDir); // fails if anything unexpected is left, which is what we want
    }
}
