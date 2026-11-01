package de.mossgrabers.convertwithmoss.format.iso.image;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Produces a logical 2048-byte-sector image for existing file-based detectors.
 * The caller owns the output and must retain it while any samples reference it.
 */
public final class CueMode1Materializer
{
    private CueMode1Materializer ()
    {
    }

    /**
     * Writes a new logical image. Never modifies the source BIN/CUE.
     * Existing targets are refused; partial output is removed on failure.
     *
     * @param cue The source cue sheet
     * @param destination New output file
     * @return Output path
     * @throws IOException On invalid input, output conflict or I/O error
     */
    public static Path materialize (final Path cue, final Path destination) throws IOException
    {
        final CueMode1Image image = CueMode1Image.open (cue);
        final Path output = destination.toAbsolutePath ().normalize ();
        final long count = image.sectorCount ();
        final long expectedBytes;
        try
        {
            expectedBytes = Math.multiplyExact (count, 2048L);
        }
        catch (final ArithmeticException ex)
        {
            throw new IOException ("Logical image too large", ex);
        }
        boolean created = false;
        try
        {
            try (final OutputStream out = Files.newOutputStream (output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
            {
                created = true;
                image.writeLogicalImage (out);
            }
            if (Files.size (output) != expectedBytes)
                throw new IOException ("Output length mismatch");
            return output;
        }
        catch (final IOException | RuntimeException ex)
        {
            if (created)
                try
                {
                    Files.deleteIfExists (output);
                }
                catch (final IOException cleanup)
                {
                    ex.addSuppressed (cleanup);
                }
            throw ex;
        }
    }
}
