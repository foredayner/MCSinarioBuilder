package com.foredayner.scenario.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Scenarios on the server, one folder each under config/scenario_builder/: the scenario text,
 * and (later steps) its sheet, layout and build progress, so work can stop and go on.
 */
public final class ScenarioStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Path root = FabricLoader.getInstance().getConfigDir().resolve("scenario_builder");

    private ScenarioStore() {
    }

    /** Tests: keep scenarios somewhere else. */
    public static void useRoot(Path path) {
        root = path;
    }

    /** What is known about a stored scenario. */
    public static final class Info {
        public String id;
        public String name;
        public int chars;
        public boolean cut;
        public long created;
        /** "text" (loaded), later "sheet", "layout", "building", "built". */
        public String stage = "text";
    }

    /** A folder-safe id from the name, unique among the stored ones. */
    static String newId(String name) throws IOException {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣_-]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "scenario";
        }
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }
        String id = base;
        for (int i = 2; Files.exists(root.resolve(id)); i++) {
            id = base + "_" + i;
        }
        return id;
    }

    public static Info save(String name, String text, boolean cut) throws IOException {
        Files.createDirectories(root);
        Info info = new Info();
        info.id = newId(name);
        info.name = name;
        info.chars = text.length();
        info.cut = cut;
        info.created = System.currentTimeMillis();
        Path dir = root.resolve(info.id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("scenario.txt"), text, StandardCharsets.UTF_8);
        writeInfo(info);
        return info;
    }

    public static void writeInfo(Info info) throws IOException {
        Files.writeString(root.resolve(info.id).resolve("info.json"), GSON.toJson(info), StandardCharsets.UTF_8);
    }

    public static String text(String id) throws IOException {
        return Files.readString(dir(id).resolve("scenario.txt"), StandardCharsets.UTF_8);
    }

    public static Info info(String id) throws IOException {
        return GSON.fromJson(Files.readString(dir(id).resolve("info.json"), StandardCharsets.UTF_8), Info.class);
    }

    /** The folder of a stored scenario (the id must be a plain folder name). */
    public static Path dir(String id) throws IOException {
        if (id == null || !id.matches("[0-9a-z가-힣_-]{1,48}")) {
            throw new IOException("bad scenario id " + id);
        }
        return root.resolve(id);
    }

    /** Newest first. */
    public static List<Info> list() {
        List<Info> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path d : dirs.toList()) {
                Path f = d.resolve("info.json");
                if (Files.isRegularFile(f)) {
                    try {
                        out.add(GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Info.class));
                    } catch (IOException | RuntimeException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
        out.sort(Comparator.comparingLong((Info i) -> i.created).reversed());
        return out;
    }

    public static String listJson() {
        return GSON.toJson(list());
    }

    public static void delete(String id) throws IOException {
        Path d = dir(id);
        if (!Files.isDirectory(d)) {
            return;
        }
        try (Stream<Path> files = Files.walk(d)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
