package de.mossgrabers.convertwithmoss.format.iso.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Standalone materialization regression test. */
public final class CueMode1MaterializerTest
{
    public static void main (final String [] args) throws Exception
    {
        final Path dir = Files.createTempDirectory ("cue-materializer-");
        try
        {
            final byte [] binary = new byte [2352 * 3];
            for (int i = 0; i < 3; i++)
                Arrays.fill (binary, i * 2352 + 16, i * 2352 + 2064, (byte) (i + 10));
            Files.write (dir.resolve ("sample.bin"), binary);
            final Path cue = dir.resolve ("sample.cue");
            Files.writeString (cue, "FILE \"sample.bin\" BINARY\nTRACK 01 MODE1/2352\nINDEX 01 00:00:01\n");
            final Path out = dir.resolve ("logical.iso");
            CueMode1Materializer.materialize (cue, out);
            final byte [] actual = Files.readAllBytes (out);
            if (actual.length != 4096 || actual[0] != 11 || actual[2048] != 12 || actual[4095] != 12)
                throw new AssertionError ("Incorrect logical image");
            try
            {
                CueMode1Materializer.materialize (cue, out);
                throw new AssertionError ("Existing output overwritten");
            }
            catch (final java.nio.file.FileAlreadyExistsException expected)
            {
                // Correct: never overwrite an existing image.
            }
            if (Files.size (out) != 4096)
                throw new AssertionError ("Existing output modified");
            System.out.println ("CueMode1Materializer checks passed");
        }
        finally
        {
            for (final String file: new String [] { "logical.iso", "sample.cue", "sample.bin" })
                Files.deleteIfExists (dir.resolve (file));
            Files.deleteIfExists (dir);
        }
    }
}
