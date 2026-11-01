# Disc-image adapter integration — source audit 01

Status: architecture reviewed; implementation and runtime tests pending.

## Verified upstream integration point

`src/main/java/de/mossgrabers/convertwithmoss/format/iso/IsoDetector.java` extends `AbstractIsoDetector<MetadataSettingsUI>`. It currently advertises `.iso`, `.img`, `.out`, `.sdk`, `.hda`. Its `readPresetFile(File)` calls `IsoFormatIdentifier.identifyIso(sourceFile)` and dispatches to Akai MPC2000, Akai S1000/S3000, E-mu, Ensoniq, Roland S5xx and S7xx decoders. The ISO_9660 case explicitly declines ordinary ISO9660 discs.

## Compatibility requirement

The decoders consume a `java.io.File`, not a generic sector-reader interface. Simply recognizing `.cue` or `.mdf` in the detector is insufficient. A safe first integration is a *temporary, read-only-derived logical image* compatible with existing decoders, with cleanup after conversion. This is only valid when the source track's layout can be faithfully represented as 2048-byte sectors. Nonstandard, multisession, mixed-mode and subchannel-dependent layouts must be rejected or passed through a future raw-sector abstraction, never silently flattened.

## Next implementation steps

1. Inspect `AbstractIsoDetector`, `IsoFormatIdentifier`, `AkaiS1000Detector` and the file-reading utilities to determine lifecycle, offsets and whether lazy sample readers retain the temporary image after detection.
2. Implement a standalone, bounded BIN/CUE Mode1/2048 and Mode1/2352 track reader with explicit track-start/LBA handling and a strict validation policy.
3. Test sector offsets, lead-in/pregap, truncation, multiple tracks, and ISO9660 signatures with synthetic fixtures.
4. Only then connect the reader to `IsoDetector` and run a full Maven build plus real vintage-image conversion tests.
5. Research MDF/MDS specifications and verified fixtures before claiming compatibility.

Do not modify upstream format decoders until the adapter contract is established. No Windows driver is required for this approach.


## Source-lifetime audit 02 (verified against branch source)

`AbstractDetector.detect(...)` dispatches detection asynchronously via a single-thread executor and delivers `IMultisampleSource` objects to consumers. `readSource(...)` can also re-read a selected source later. Consequently, deleting a temporary logical image immediately after `IsoDetector.readPresetFile` returns is unsafe unless every dispatched sample is demonstrably fully materialized and later reads recreate the same image.

`AkaiS1000Sample(AkaiDiskImage, int)` eagerly reads PCM into an in-memory `short[]` while the `AkaiDiskImage` is open, and `AbstractIsoDetector.processAkaiS1000Disk` closes that image before returning. This is encouraging for Akai S1000 but **does not prove** that the other ISO-dispatched sampler families are eager. `AkaiDiskImage` also casts the file length to an `int`, so images exceeding 2 GiB require separate handling.

Integration gate: audit each dispatched family for deferred file references; define a stable cache path or managed lifecycle before wiring `.cue` into `IsoDetector`. Keep the current materializer standalone until that contract is settled.

The standalone materializer currently opens the BIN file once per logical sector through `readSector`; this is correct for small fixtures but needs a streaming implementation before practical full-disc conversion.

## Initial ISO detector integration — experimental

`IsoDetector` now advertises `.cue` and materializes supported single-track Mode 1 images before calling the existing ISO-format dispatch. Temporary logical images are marked `deleteOnExit` to avoid invalidating lazy source reads during the process lifetime; abrupt termination may leave files behind. This is a provisional lifecycle policy, not a robust managed cache.

**Known limitations before release:** temporary image names/paths can leak into output metadata and relative path calculations; each source re-read creates a new temporary image; available disk capacity is not preflighted; full application Maven compilation and actual sampler-disc conversion are not yet verified. The initial adapter does not support mixed-mode/multitrack images. Do not merge into main before these are resolved and tested.
