package de.mossgrabers.convertwithmoss.format.iso.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Dependency-free sector-reader regression checks. Run with:
 * java -ea de.mossgrabers.convertwithmoss.format.iso.image.CueMode1ImageTest
 */
public final class CueMode1ImageTest
{
    public static void main (final String [] args) throws Exception
    {
        final Path dir = Files.createTempDirectory ("cue-mode1-test-");
        try
        {
            for (final int size: new int []
            {
                2048,
                2352
            })
            {
                final byte [] bin = new byte [size * 3];
                for (int sector = 0; sector < 3; sector++)
                    Arrays.fill (bin, sector * size + (size == 2352 ? 16 : 0), sector * size + (size == 2352 ? 16 : 0) + 2048, (byte) (sector + 1));
                Files.write (dir.resolve ("test.bin"), bin);
                final Path cue = dir.resolve ("test.cue");
                Files.writeString (cue, "FILE \"test.bin\" BINARY\n  TRACK 01 MODE1/" + size + "\n    INDEX 01 00:00:01\n");
                final CueMode1Image image = CueMode1Image.open (cue);
                require (image.sectorCount () == 2, "Sector count");
                require (image.readSector (0)[0] == 2, "First logical sector");
                require (image.readSector (1)[2047] == 3, "Last logical sector");
                expectFailure (() -> image.readSector (2));
                expectFailure (() -> image.readSector (-1));
            }

            final Path cue = dir.resolve ("test.cue");
            Files.writeString (cue, "FILE \"test.bin\" BINARY\nTRACK 01 MODE1/2352\nTRACK 02 AUDIO\nINDEX 01 00:00:00\n");
            expectFailure (() -> CueMode1Image.open (cue));
            Files.writeString (cue, "FILE \"test.bin\" BINARY\nTRACK 01 MODE1/2352\nINDEX 01 00:61:00\n");
            expectFailure (() -> CueMode1Image.open (cue));
            Files.writeString (cue, "FILE \"test.bin\" BINARY\nTRACK 01 MODE1/2352\nINDEX 01 00:00:04\n");
            expectFailure (() -> CueMode1Image.open (cue));
            System.out.println ("CueMode1Image sector checks passed");
        }
        finally
        {
            Files.deleteIfExists (dir.resolve ("test.cue"));
            Files.deleteIfExists (dir.resolve ("test.bin"));
            Files.deleteIfExists (dir);
        }
    }

    private static void require (final boolean condition, final String message)
    {
        if (!condition)
            throw new AssertionError (message);
    }

    private static void expectFailure (final ThrowingOperation operation) throws Exception
    {
        try
        {
            operation.run ();
        }
        catch (final IOException expected)
        {
            return;
        }
        throw new AssertionError ("Expected IOException");
    }

    @FunctionalInterface
    private interface ThrowingOperation
    {
        void run () throws Exception;
    }
}
