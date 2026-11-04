package com.foredayner.scenario.layout;

import com.foredayner.scenario.sheet.ScenarioSheet;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MapImagesTest {

    @Test
    void theMapIsSqueezedToASquareThumbnail() throws Exception {
        BufferedImage wide = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(wide, "png", png);
        byte[] thumb = MapImages.thumbnail(png.toByteArray());
        assertNotNull(thumb);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(thumb));
        assertEquals(MapImages.THUMB, back.getWidth());
        assertEquals(MapImages.THUMB, back.getHeight());
        assertNull(MapImages.thumbnail(new byte[] { 1, 2, 3 }), "not a picture: no thumbnail");
    }

    @Test
    void webpMapsGetAThumbnailToo() throws Exception {
        for (String b64 : new String[] {
            "UklGRkAAAABXRUJQVlA4IDQAAACQAQCdASoQAAwAAMASJaACdLoAA5gA/vwhH/+c3//90Cf+NI/90Cfha/8B/90CY5/9zaAA", // lossy
            "UklGRigAAABXRUJQVlA4TBwAAAAvD8ACAA8wHvdQvRXy/EcLxILJ/KV7Q4jof+gC" }) { // lossless
            byte[] thumb = MapImages.thumbnail(java.util.Base64.getDecoder().decode(b64));
            assertNotNull(thumb, b64);
            BufferedImage back = ImageIO.read(new ByteArrayInputStream(thumb));
            assertEquals(MapImages.THUMB, back.getWidth());
            // Left half blue, right half red, as drawn.
            int left = back.getRGB(40, 128), right = back.getRGB(216, 128);
            assertTrue((left & 0xFF) > 150 && ((left >> 16) & 0xFF) < 100, Integer.toHexString(left));
            assertTrue(((right >> 16) & 0xFF) > 150 && (right & 0xFF) < 100, Integer.toHexString(right));
        }
    }

    @Test
    void theAiIsToldToFollowTheMap() throws Exception {
        ScenarioSheet sheet = ScenarioSheet.fromJson(Files.readString(Path.of("src/gametest/resources/scenarios/greymouth.sheet.json")));
        sheet.normalize();
        TownLayout draft = TownLayout.auto(sheet);
        String with = LayoutPrompt.layout(sheet, draft, true).user();
        String without = LayoutPrompt.layout(sheet, draft, false).user();
        assertTrue(with.contains("A MAP of the town is attached"), with);
        assertTrue(with.contains("x=" + ScenarioSheet.groundSize(sheet)) && with.contains("\"extra\"") && with.contains("\"map_kind\""), with);
        assertFalse(without.contains("MAP of the town"), without);
        assertEquals("indoor", LayoutPrompt.mapKind("{\"map_kind\": \"indoor\", \"places\":[]}"));
        assertTrue(LayoutPrompt.extras("{\"places\":[]}").isEmpty());
    }
}
