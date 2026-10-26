package com.foredayner.scenario.town;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Where a scenario's town is built and how far building got (saved as build.json). */
public final class BuildState {

    /** World position of layout cell (0, 0) at the level buildings stand on (the ground is one below). */
    public boolean placed;
    public int originX;
    public int originY;
    public int originZ;
    public String dimension = "minecraft:overworld";
    /** idle, running, done, stopped, undoing. */
    public String status = "idle";
    /** waiting, working, done, stopped. */
    public String ground = "waiting";
    public int groundPercent;
    public Map<String, Place> places = new LinkedHashMap<>();
    public int tokensIn;
    public int tokensOut;
    public String message = "";

    public static final class Place {
        /** waiting, designing, placing, done, failed, stopped, cleared. */
        public String state = "waiting";
        public String error = "";
        public String tag = "";
        public int[] min;
        public int[] max;
        /** Marked spots (clue:..., npc:...) in world positions. */
        public Map<String, List<int[]>> marks = new HashMap<>();
        public boolean prop;
    }

    public Place place(String id) {
        return places.computeIfAbsent(id, k -> new Place());
    }

    public boolean busy() {
        return "running".equals(status) || "undoing".equals(status);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static BuildState fromJson(String json) {
        BuildState s = GSON.fromJson(json, BuildState.class);
        if (s == null) {
            s = new BuildState();
        }
        if (s.places == null) {
            s.places = new LinkedHashMap<>();
        }
        return s;
    }

    /** Counts for the progress line: {done, failed, all}. */
    public int[] counts() {
        int done = 0, failed = 0;
        for (Place p : places.values()) {
            done += "done".equals(p.state) ? 1 : 0;
            failed += "failed".equals(p.state) ? 1 : 0;
        }
        return new int[] { done, failed, places.size() };
    }

    static List<int[]> list(int[] p) {
        List<int[]> l = new ArrayList<>();
        l.add(p);
        return l;
    }
}
