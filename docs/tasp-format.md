# The `.tasp` recording format (format version 1)

A `.tasp` file is a zip archive. While recording, the same files live in a `<name>.tasp.part/`
directory; finishing (or recovering) a recording packs that directory into the zip. Everything here is
readable without Minecraft through the `core` module (`dev.dbenton.tasplayback.core`).

## Files

| File | Content | Zip method |
|---|---|---|
| `meta.json` | Format/mod/Minecraft versions, loader, window and GUI scale, language, resource packs with SHA-1, render-affecting options, tickrate history | deflate |
| `segments.json` | Recording segments (one per rewind / savestate load) and user overrides | deflate |
| `markers.json` | User markers and chapters | deflate |
| `frames.bin` | One record per rendered frame: camera, first-person, mouse, input | stored |
| `gui.bin` | GUI state events and keyframes (payloads version-specific) | stored |
| `hud.bin` | HUD / inventory changes (payloads version-specific) | stored |
| `sounds.bin` | Sound play / update / stop events | stored |
| `world.bin` | Clientbound packets and local-player state (payloads version-specific) | stored |
| `guicap.bin` | Optional captured GUI layer | stored |
| `status.json` | Only in `.part` directories: `"recording"` or `"complete"` | — |

Stream files are stored rather than deflated in the zip because their chunks are already compressed.
A recording made on one version node plays back on that node only (`"crossVersionPlayback": false`);
item stacks, text and packets are stored in the game's own encoding.

## Time keys

Every record carries a key `(segment, tick, partial, frame)`:

- `segment`: the recording segment (see `segments.json`).
- `tick` + `partial`: **game time**. The partial tick is in [0, 1). Wall-clock time is metadata only.
- `frame`: a counter of rendered frames, strictly increasing across the whole recording, including across
  segments. Records produced between two frames (for example sounds played during a tick) carry the
  index of the next frame.

## Stream files

All integers are big-endian.

```
file        := fileHeader chunk*
fileHeader  := "TASP" u8(kind=1) u8(streamId) u16(streamVersion) u32(crc32 of the previous 8 bytes)
chunk       := chunkHeader body
chunkHeader := "CHNK" u32(seq) u32(recordCount) u32(rawLength) u32(bodyLength)
               timeKey(first) timeKey(last) u32(crc32(body)) u32(crc32 of the previous 72 header bytes)
timeKey     := i32(segment) i64(tick) f32(partial) i64(frame)                  (24 bytes)
body        := raw DEFLATE (no zlib header) of recordCount encoded records
```

Stream ids: 1 frames, 2 gui, 3 hud, 4 sounds, 5 world, 6 guicap.

Each chunk is self-contained: delta coding restarts at the start of every chunk, so any chunk decodes
without the ones before it (seeking), and a reader keeps every chunk before the first damaged one
(crash recovery). Chunks are cut every 4096 records, every 1 MiB, or about once per second.

### Varints

Unsigned LEB128. Signed values use zigzag (`(n << 1) ^ (n >> 63)`).

### Event records (gui, hud, sounds, world, guicap)

```
record := keyDelta varint(type) varint(payloadLength) payload
keyDelta := zigzag(Δsegment) zigzag(Δtick) varint(bits(partial) XOR bits(prevPartial)) zigzag(Δframe)
```

The previous key starts at `(0, 0, 0.0, 0)` in each chunk.

### Sound payloads (`sounds.bin`)

| type | payload |
|---|---|
| 1 PLAY | varint id, string eventId, string soundPath (the resolved variant), string category, u8 flags (1 streaming, 2 looping, 4 relative, 8 hasSeed), u8 attenuation (0 none, 1 linear), f32 attenuationDistance, [i64 seed], varint delayTicks, f32 volume, f32 pitch, f64 x, f64 y, f64 z |
| 2 STOP | varint id |
| 3 UPDATE | varint id, f32 volume, f32 pitch, f64 x, f64 y, f64 z |
| 4 STOP_ALL | (empty) |

Strings are a varint byte length followed by UTF-8.

### Frame records (`frames.bin`, version 1)

Each field is coded against the same field of the previous frame in the chunk (an all-zero record at
the start of a chunk): integers as zigzag deltas, `f32`/`f64` as a varint of the XOR of the raw IEEE bits
(lossless). Field order is defined by `FrameCodec` and listed in `FrameRecord`: the time key, wall
nanoseconds, eye position, yaw/pitch/roll, FOV and FOV modifier, view-bob inputs, hurt tilt, portal and
nausea intensity, perspective, detached flag, hand animation state, held-item references, crosshair hit
result, block-break progress, raw and GUI-scaled mouse position, mouse buttons, held keys (length + 1, or 0
for "unchanged"), window and framebuffer size, GUI scale, optional framebuffer checksum, and a
version-specific extension blob (length + 1, or 0 for "unchanged").

## Canonical timeline

`segments.json` lists segments with `parent` and `link` (where the loaded state came from). The
canonical timeline walks from the final segment up the parent links; each segment contributes its frames
from its own start until the link point of the next segment on the path. Rewound branches drop out
automatically. Users can pick a different final segment (`finalSegmentOverride`), re-link a segment, or
mark a detected rewind as a false positive (`ignored`).
