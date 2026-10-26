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
    void theAiIsToldToFollowTheMap() throws Exception {
        ScenarioSheet sheet = ScenarioSheet.fromJson(Files.readString(Path.of("src/gametest/resources/scenarios/greymouth.sheet.json")));
        sheet.normalize();
        TownLayout draft = TownLayout.auto(sheet);
        String with = LayoutPrompt.layout(sheet, draft, true).user();
        String without = LayoutPrompt.layout(sheet, draft, false).user();
        assertTrue(with.contains("A MAP of the town is attached"), with);
        assertTrue(with.contains("x=160") && with.contains("\"unnamed\""), with);
        assertFalse(without.contains("MAP of the town"), without);
        assertEquals(3, LayoutPrompt.unnamed("{\"places\":[], \"unnamed\": 3}"));
        assertEquals(-1, LayoutPrompt.unnamed("{\"places\":[]}"));
    }
}
