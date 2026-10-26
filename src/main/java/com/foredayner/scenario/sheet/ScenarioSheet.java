package com.foredayner.scenario.sheet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the AI read from a scenario, in the shape the later steps need: the era and mood, the
 * places to build (with rooms), the NPCs and clues and where they are, how places connect, and
 * the lie of the land. The GM can check and change it before the layout is made.
 */
public final class ScenarioSheet {

    public static final List<String> SIZES = List.of("S", "M", "L", "XL");
    public static final List<String> CLUE_FORMS = List.of("book", "note", "map", "item");
    public static final List<String> SIDES = List.of("none", "north", "east", "south", "west");
    public static final List<String> LINKS = List.of("road", "path", "secret");
    /** How a place looks after: kept, worn, abandoned, sinister. */
    public static final List<String> CONDITIONS = List.of("kept", "worn", "abandoned", "sinister");
    /** At most this many places fit a 160x160 town with room for streets. */
    public static final int MAX_PLACES = 14;

    public String title = "";
    /** "1920s" or "modern" (or another era the scenario names). */
    public String era = "1920s";
    /** Period and culture of the buildings ({@link Period}): picks checklists, reference cards and the ground. */
    public String period = "";
    public String setting = "";
    public String mood = "";
    public String timeOfDay = "night";
    public String weather = "rain";
    /** Building style rules shared by the whole town (materials as Minecraft blocks). */
    public String style = "";
    /** The town's architecture guide (written by the AI from the era and place, edited by the keeper). */
    public String styleGuide = "";
    public List<Place> places = new ArrayList<>();
    public List<Npc> npcs = new ArrayList<>();
    public List<Clue> clues = new ArrayList<>();
    public List<Link> links = new ArrayList<>();
    public Terrain terrain = new Terrain();

    public static final class Place {
        public String id = "";
        public String name = "";
        /** house, inn, shop, church, library, police, newspaper, mansion, farm, cemetery, woods, dock, lighthouse... */
        public String kind = "house";
        /** S 11x11, M 15x15, L 21x21, XL 31x31. */
        public String size = "M";
        public int floors = 1;
        public boolean basement;
        public List<Room> rooms = new ArrayList<>();
        /** Which scenes happen here (short). */
        public List<String> scenes = new ArrayList<>();
        public String notes = "";
        /** A town house added to make the town feel alive, not from the scenario. */
        public boolean filler;
        /** Outdoor places (cemetery, woods, dock) have no building, just ground features. */
        public boolean outdoor;
        /** kept, worn, abandoned or sinister. */
        public String condition = "kept";
        /** A picture of a real building to follow: a web address, or "file:<name>" kept with the scenario. */
        public String refImage = "";
        /** What the AI saw in that picture, as building directions (made when the picture is set). */
        public String refDescription = "";
        /** Built from the same design as this other place (rows of identical houses): one AI design, placed again. */
        public String sameAs = "";
    }

    public static final class Room {
        public String name = "";
        public String purpose = "";
    }

    public static final class Npc {
        public String name = "";
        public String role = "";
        public String place = "";
        public String room = "";
        public String notes = "";
    }

    public static final class Clue {
        public String id = "";
        public String name = "";
        /** What it says or shows, 1-3 sentences, in the scenario's language. */
        public String text = "";
        public String form = "note";
        public String place = "";
        public String room = "";
        public boolean hidden;
    }

    public static final class Link {
        public String from = "";
        public String to = "";
        public String kind = "road";
    }

    public static final class Terrain {
        /** Which edge of the town is sea (or none). */
        public String coast = "none";
        /** Which edge has woods (or none). */
        public String woods = "none";
        public String notes = "";
    }

    // ------------------------------------------------------------------ JSON

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    /** Read a sheet from the AI's answer: the first JSON object in it (code fences and talk around it are ignored). */
    public static ScenarioSheet fromAnswer(String answer) {
        String json = firstObject(answer);
        if (json == null) {
            throw new IllegalArgumentException("no JSON object in the answer");
        }
        ScenarioSheet sheet = GSON.fromJson(JsonParser.parseString(json), ScenarioSheet.class);
        if (sheet == null) {
            throw new IllegalArgumentException("empty JSON");
        }
        return sheet;
    }

    public static ScenarioSheet fromJson(String json) {
        return GSON.fromJson(json, ScenarioSheet.class);
    }

    static String firstObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return text.substring(start, i + 1);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ checking

    /**
     * Make the sheet consistent where that is safe (missing ids, unknown sizes, lists that are
     * null) and return the problems the AI (or the GM) must fix: unknown places in NPCs, clues
     * and links, too many places, duplicate ids.
     */
    public List<String> normalize() {
        List<String> problems = new ArrayList<>();
        if (places == null) {
            places = new ArrayList<>();
        }
        if (npcs == null) {
            npcs = new ArrayList<>();
        }
        if (clues == null) {
            clues = new ArrayList<>();
        }
        if (links == null) {
            links = new ArrayList<>();
        }
        if (terrain == null) {
            terrain = new Terrain();
        }
        if (styleGuide == null) {
            styleGuide = "";
        }
        if (style == null) {
            style = "";
        }
        if (!Period.known(period)) {
            period = Period.guess(era, setting, title, style);
        }
        terrain.coast = SIDES.contains(lower(terrain.coast)) ? lower(terrain.coast) : "none";
        terrain.woods = SIDES.contains(lower(terrain.woods)) ? lower(terrain.woods) : "none";
        if (places.isEmpty()) {
            problems.add("places is empty: list the places to build");
        }
        if (places.size() > MAX_PLACES) {
            problems.add("there are " + places.size() + " places; keep at most " + MAX_PLACES + " (merge small ones, drop fillers)");
        }
        Set<String> ids = new HashSet<>();
        int n = 1;
        for (Place p : places) {
            if (p.id == null || p.id.isBlank()) {
                p.id = "place" + n;
            }
            p.id = slug(p.id);
            if (!ids.add(p.id)) {
                problems.add("place id '" + p.id + "' is used twice");
            }
            n++;
            p.size = p.size == null ? "M" : p.size.trim().toUpperCase(Locale.ROOT);
            if (!SIZES.contains(p.size)) {
                p.size = "M";
            }
            p.floors = Math.max(1, Math.min(4, p.floors));
            p.condition = CONDITIONS.contains(lower(p.condition)) ? lower(p.condition) : "kept";
            // Places under the ground cannot be built as buildings: the town shows their entrance.
            String look = (p.kind + " " + p.name + " " + p.id).toLowerCase(Locale.ROOT);
            if ("cave".equals(p.kind) || look.matches(".*(동굴|땅굴|지하 ?묘지|광산|터널|\\bcave|cavern|grotto|\\bmine\\b|tunnel|catacomb).*")) {
                p.kind = "cave";
                p.outdoor = true;
            }
            if (p.refImage == null) {
                p.refImage = "";
            }
            if (p.refDescription == null) {
                p.refDescription = "";
            }
            p.sameAs = p.sameAs == null ? "" : slug(p.sameAs);
            if (p.rooms == null) {
                p.rooms = new ArrayList<>();
            }
            if (p.scenes == null) {
                p.scenes = new ArrayList<>();
            }
            if (p.name == null || p.name.isBlank()) {
                p.name = p.id;
            }
        }
        for (Place p : places) {
            if (!p.sameAs.isEmpty() && (p.sameAs.equals(p.id) || !ids.contains(p.sameAs))) {
                problems.add("place " + p.id + " is the same as '" + p.sameAs + "', which is not another place");
                p.sameAs = "";
            }
        }
        for (Npc c : npcs) {
            if (!ids.contains(slug(c.place))) {
                problems.add("NPC '" + c.name + "' is in place '" + c.place + "', which is not in places (use a place id)");
            } else {
                c.place = slug(c.place);
            }
        }
        Set<String> clueIds = new HashSet<>();
        int k = 1;
        for (Clue c : clues) {
            if (c.id == null || c.id.isBlank()) {
                c.id = "clue" + k;
            }
            c.id = slug(c.id);
            if (!clueIds.add(c.id)) {
                problems.add("clue id '" + c.id + "' is used twice");
            }
            k++;
            c.form = CLUE_FORMS.contains(lower(c.form)) ? lower(c.form) : "note";
            if (!ids.contains(slug(c.place))) {
                problems.add("clue '" + c.name + "' is in place '" + c.place + "', which is not in places (use a place id)");
            } else {
                c.place = slug(c.place);
            }
        }
        for (Link l : links) {
            l.kind = LINKS.contains(lower(l.kind)) ? lower(l.kind) : "road";
            if (!ids.contains(slug(l.from)) || !ids.contains(slug(l.to))) {
                problems.add("link " + l.from + " -> " + l.to + " names a place that is not in places");
            } else {
                l.from = slug(l.from);
                l.to = slug(l.to);
            }
        }
        return problems;
    }

    /** The sheet's period, or a guess from its words when it has none. */
    public String period() {
        return Period.known(period) ? period : Period.guess(era, setting, title, style);
    }

    public Place place(String id) {
        for (Place p : places) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    /** "Old Mill" -> "old_mill"; keeps Korean letters. */
    public static String slug(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z가-힣_]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** Block footprint of a size code. */
    public static int footprint(String size) {
        return switch (size) {
            case "S" -> 11;
            case "L" -> 21;
            case "XL" -> 31;
            default -> 15;
        };
    }
}
