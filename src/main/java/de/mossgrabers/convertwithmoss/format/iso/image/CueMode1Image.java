package de.mossgrabers.convertwithmoss.format.iso.image;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict, read-only single-track BIN/CUE Mode 1 reader.
 * Does not flatten audio, multisession or unsupported sector layouts.
 */
public final class CueMode1Image
{
    private static final Pattern FILE = Pattern.compile("^FILE\\s+\\\"([^\\\"]+)\\\"\\s+BINARY$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACK = Pattern.compile("^TRACK\\s+(\\d+)\\s+(MODE1/2048|MODE1/2352)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX = Pattern.compile("^INDEX\\s+(\\d+)\\s+(\\d+):(\\d+):(\\d+)$", Pattern.CASE_INSENSITIVE);
    private final Path binary;
    private final int sectorSize;
    private final long firstSector;

    private CueMode1Image (final Path binary, final int sectorSize, final long firstSector)
    {
        this.binary = binary;
        this.sectorSize = sectorSize;
        this.firstSector = firstSector;
    }

    public static CueMode1Image open (final Path cue) throws IOException
    {
        final List<String> lines = Files.readAllLines (cue);
        String file = null;
        int size = 0;
        int tracks = 0;
        long index = -1;
        for (final String raw: lines)
        {
            final String line = raw.strip ();
            if (line.isEmpty () || line.toUpperCase (Locale.ROOT).startsWith ("REM "))
                continue;
            Matcher m = FILE.matcher (line);
            if (m.matches ())
            {
                if (file != null)
                    throw new IOException ("Multiple FILE entries unsupported");
                file = m.group (1);
                continue;
            }
            m = TRACK.matcher (line);
            if (m.matches ())
            {
                tracks++;
                size = m.group (2).equalsIgnoreCase ("MODE1/2352") ? 2352 : 2048;
                continue;
            }
            m = INDEX.matcher (line);
            if (m.matches ())
            {
                if (Integer.parseInt (m.group (1)) == 1)
                {
                    if (index >= 0)
                        throw new IOException ("Duplicate INDEX 01");
                    final int minutes = Integer.parseInt (m.group (2));
                    final int seconds = Integer.parseInt (m.group (3));
                    final int frames = Integer.parseInt (m.group (4));
                    if (seconds >= 60 || frames >= 75)
                        throw new IOException ("Invalid MSF time");
                    index = ((long) minutes * 60 + seconds) * 75 + frames;
                }
                continue;
            }
            // Reject unknown layout directives rather than silently misreading sectors.
            if (!line.toUpperCase (Locale.ROOT).startsWith ("TITLE ") && !line.toUpperCase (Locale.ROOT).startsWith ("PERFORMER "))
                throw new IOException ("Unsupported CUE directive: " + line);
        }
        if (file == null || tracks != 1 || index < 0)
            throw new IOException ("Exactly one Mode 1 track with INDEX 01 is required");
        final Path bin = cue.toAbsolutePath ().getParent ().resolve (file).normalize ();
        if (!Files.isRegularFile (bin))
            throw new IOException ("Binary image not found: " + bin);
        final long bytes = Files.size (bin);
        if (bytes % size != 0 || index >= bytes / size)
            throw new IOException ("Truncated or misaligned image");
        return new CueMode1Image (bin, size, index);
    }

    public long sectorCount () throws IOException
    {
        return Files.size (this.binary) / this.sectorSize - this.firstSector;
    }

    /**
     * Stream the logical data sectors without reopening the binary for every sector.
     * The output stream is owned by the caller and is not closed here.
     */
    public void writeLogicalImage (final java.io.OutputStream out) throws IOException
    {
        final long count = this.sectorCount ();
        final byte [] data = new byte [2048];
        try (final RandomAccessFile in = new RandomAccessFile (this.binary.toFile (), "r"))
        {
            in.seek (Math.multiplyExact (this.firstSector, this.sectorSize));
            for (long sector = 0; sector < count; sector++)
            {
                if (this.sectorSize == 2352)
                    in.seek (in.getFilePointer () + 16);
                in.readFully (data);
                out.write (data);
                if (this.sectorSize == 2352)
                    in.seek (in.getFilePointer () + 288);
            }
        }
    }

    public byte [] readSector (final long lba) throws IOException
    {
        if (lba < 0 || lba >= this.sectorCount ())
            throw new IOException ("Sector outside image: " + lba);
        final byte [] data = new byte [2048];
        try (final RandomAccessFile in = new RandomAccessFile (this.binary.toFile (), "r"))
        {
            final long offset = Math.multiplyExact (Math.addExact (this.firstSector, lba), this.sectorSize);
            in.seek (offset + (this.sectorSize == 2352 ? 16 : 0));
            in.readFully (data);
        }
        return data;
    }
}
