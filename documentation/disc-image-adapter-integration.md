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
