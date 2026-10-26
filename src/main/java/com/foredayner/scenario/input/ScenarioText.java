package com.foredayner.scenario.input;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Turning a scenario file into plain text: .txt / .md in UTF-8 or Korean Windows encoding
 * (CP949), or the text of a PDF (pictures are ignored). The text is tidied (line ends, extra
 * blank lines) so the AI gets it compactly.
 */
public final class ScenarioText {

    /** Longest scenario kept (characters); longer ones are cut and the AI is told so. */
    public static final int MAX_CHARS = 60_000;

    private ScenarioText() {
    }

    /** A loaded scenario: its name (file name), the text, and whether it was cut short. */
    public record Loaded(String name, String text, boolean cut, int originalChars) {
    }

    public static Loaded load(Path file) throws IOException {
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        String text;
        if (lower.endsWith(".pdf")) {
            text = pdfText(Files.readAllBytes(file));
        } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            text = decode(Files.readAllBytes(file));
        } else {
            throw new IOException("지원하지 않는 파일이에요: " + name + " (.txt .md .pdf만 돼요)");
        }
        int dot = name.lastIndexOf('.');
        return fromText(dot > 0 ? name.substring(0, dot) : name, text);
    }

    public static Loaded fromText(String name, String raw) {
        String text = tidy(raw);
        int original = text.length();
        boolean cut = original > MAX_CHARS;
        if (cut) {
            text = text.substring(0, MAX_CHARS);
        }
        return new Loaded(name == null || name.isBlank() ? "시나리오" : name.trim(), text, cut, original);
    }

    /** UTF-8 if the bytes are valid UTF-8 (with or without a BOM), otherwise CP949 (Korean Windows). */
    public static String decode(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(bytes, start, bytes.length - start);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(buffer).toString();
        } catch (CharacterCodingException notUtf8) {
            Charset korean = Charset.isSupported("MS949") ? Charset.forName("MS949") : Charset.forName("EUC-KR");
            return new String(bytes, korean);
        }
    }

    public static String pdfText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /** Unix line ends, no trailing spaces, at most one blank line in a row, no page-break characters. */
    public static String tidy(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\f', '\n').replace(" ", " ");
        StringBuilder out = new StringBuilder(t.length());
        int blank = 0;
        for (String line : t.split("\n", -1)) {
            String l = line.stripTrailing();
            if (l.isBlank()) {
                if (++blank > 1) {
                    continue;
                }
                out.append('\n');
                continue;
            }
            blank = 0;
            out.append(l).append('\n');
        }
        return out.toString().strip();
    }
}
