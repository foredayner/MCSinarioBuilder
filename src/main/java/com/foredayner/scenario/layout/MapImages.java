package com.foredayner.scenario.layout;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** The small picture of the keeper's map shown under the layout (the map stretched over the square ground). */
public final class MapImages {

    /** Side of the thumbnail in pixels. */
    public static final int THUMB = 256;

    private MapImages() {
    }

    /**
     * The map squeezed to a {@link #THUMB} square PNG, the way it lies on the ground (png, jpeg, gif
     * or webp); null when it is not a picture.
     */
    public static byte[] thumbnail(byte[] data) {
        try {
            BufferedImage in = read(data);
            if (in == null) {
                return null;
            }
            BufferedImage out = new BufferedImage(THUMB, THUMB, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(in, 0, 0, THUMB, THUMB, null);
            g.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Any picture the keeper may give: what ImageIO reads, and webp by the bundled reader (asked by name, not found by ImageIO's lookup). */
    static BufferedImage read(byte[] data) throws IOException {
        BufferedImage in = ImageIO.read(new ByteArrayInputStream(data));
        if (in != null || !webp(data)) {
            return in;
        }
        ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(stream);
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    static boolean webp(byte[] d) {
        return d != null && d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
            && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }
}
