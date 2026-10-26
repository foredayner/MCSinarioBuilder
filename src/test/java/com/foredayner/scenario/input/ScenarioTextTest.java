package com.foredayner.scenario.input;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioTextTest {

    @Test
    void readsUtf8WithOrWithoutBom() {
        String text = "그레이머스의 등불\n부두";
        assertEquals(text, ScenarioText.decode(text.getBytes(StandardCharsets.UTF_8)));
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] bom = new byte[body.length + 3];
        bom[0] = (byte) 0xEF;
        bom[1] = (byte) 0xBB;
        bom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bom, 3, body.length);
        assertEquals(text, ScenarioText.decode(bom));
    }

    @Test
    void readsKoreanWindowsFiles() {
        String text = "위트모어 저택의 서재";
        byte[] cp949 = text.getBytes(Charset.forName("MS949"));
        assertEquals(text, ScenarioText.decode(cp949));
    }

    @Test
    void tidiesLinesAndCutsLongOnes() {
        assertEquals("a\n\nb", ScenarioText.tidy("a  \r\n\r\n\r\n\r\nb\r\n"));
        String longText = "가".repeat(ScenarioText.MAX_CHARS + 10);
        ScenarioText.Loaded loaded = ScenarioText.fromText("긴 것", longText);
        assertTrue(loaded.cut());
        assertEquals(ScenarioText.MAX_CHARS, loaded.text().length());
        assertEquals(ScenarioText.MAX_CHARS + 10, loaded.originalChars());
    }

    @Test
    void readsPdfText() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("The lighthouse keeper is missing.");
                content.endText();
            }
            doc.save(bytes);
        }
        assertTrue(ScenarioText.pdfText(bytes.toByteArray()).contains("lighthouse keeper"));
    }
}
