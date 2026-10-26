package com.foredayner.scenario.layout;

import javax.imageio.ImageIO;
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
     * The map squeezed to a {@link #THUMB} square PNG, the way it lies on the ground; null when Java
     * cannot read the picture (a webp map is still shown to the AI, just not under the layout).
     */
    public static byte[] thumbnail(byte[] data) {
        try {
            BufferedImage in = ImageIO.read(new ByteArrayInputStream(data));
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
}
