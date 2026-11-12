package com.foredayner.scenario.town;

import com.example.ai.build.BuildTaskQueue;
import com.example.api.AiBuildApi;
import com.foredayner.scenario.ScenarioBuilderMod;
import com.foredayner.scenario.layout.LayoutService;
import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.layout.Underground;
import com.foredayner.scenario.sheet.Period;
import com.foredayner.scenario.net.ScenarioNet;
import com.foredayner.scenario.sheet.ScenarioSheet;
import com.foredayner.scenario.sheet.SheetService;
import com.foredayner.scenario.store.ScenarioConfig;
import com.foredayner.scenario.store.ScenarioStore;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Building a scenario's town where the keeper chose: the ground first (a few columns per tick),
 * then each place: a building designed by the AI Build Assistant for its lot, or for an outdoor
 * place its ground and one AI prop. Up to {@link #parallel} designs run at once; buildings are
 * placed once the ground is ready. A place can be built again or cleared; the whole town can be
 * undone while the server keeps the land as it was (in memory).
 */
public final class BuildService {

    /** How the buildings and props are designed (the AI; tests use fixed scripts). */
    public interface Designer {
        CompletableFuture<AiBuildApi.Design> building(ServerWorld world, BlockPos near, AiBuildApi.Site site, AiBuildApi.BuildingRequest req);

        CompletableFuture<AiBuildApi.Design> object(ServerWorld world, BlockPos near, String description, double maxSize, String model);
    }

    public static final Designer AI = new Designer() {
        @Override
        public CompletableFuture<AiBuildApi.Design> building(ServerWorld world, BlockPos near, AiBuildApi.Site site, AiBuildApi.BuildingRequest req) {
            return AiBuildApi.designBuilding(world, near, site, req, line -> ScenarioBuilderMod.LOG.info("Building design: {}", line));
        }

        @Override
        public CompletableFuture<AiBuildApi.Design> object(ServerWorld world, BlockPos near, String description, double maxSize, String model) {
            return AiBuildApi.designObject(world, near, description, maxSize, model, line -> ScenarioBuilderMod.LOG.info("Prop design: {}", line));
        }
    };

    public static Designer designer = AI;
    /** Designs asked for at the same time. */
    public static int parallel = 3;
    /** Ground columns per tick. */
    public static int columnsPerTick = 300;
    public static final double PROP_SIZE = 4.0;
    public static final int PROP_LOT = BuildPrompts.PROP_LOT;
    public static final int PROP_HEIGHT = BuildPrompts.PROP_HEIGHT;

    private static final Map<String, Job> JOBS = new HashMap<>();
    /** The land as it was before each town (memory only). */
    private static final Map<String, TownSnapshot> SNAPSHOTS = new HashMap<>();

    private BuildService() {
    }

    // ------------------------------------------------------------------ state on disk

    static boolean hasOutside(com.foredayner.scenario.layout.TownLayout layout) {
        if (layout.building()) {
            return false;
        }
        for (var s : layout.sites) {
            if (layout.outside(s)) {
                return true;
            }
        }
        return false;
    }

    /** Which world this server runs: the build record is kept per world (a scenario built in one world starts fresh in another). */
    private static String worldKey = "default";

    public static void world(MinecraftServer server) {
        try {
            String root = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().toString();
            worldKey = "w" + Integer.toHexString(root.hashCode());
        } catch (RuntimeException e) {
            worldKey = "default";
        }
    }

    /** This world's build record of a scenario. */
    static java.nio.file.Path buildFile(String id) throws IOException {
        return ScenarioStore.dir(id).resolve("build-" + worldKey + ".json");
    }

    /** Start this world's building of a scenario over: its record is cleared (the blocks in the world stay). */
    /** What to do once a scenario's running job has stopped (새로 짓기 or 전체 되돌리기 pressed while it ran). */
    private static final Map<String, Runnable> AFTER = new HashMap<>();

    public static void restart(ServerPlayerEntity player, String id) {
        if (JOBS.containsKey(id)) {
            // Building: stop first, start over when it has stopped.
            AFTER.put(id, () -> restart(player, id));
            stop(player, id);
            say(player, "멈추는 대로 새로 시작해요.", id);
            return;
        }
        BuildState state = new BuildState();
        SNAPSHOTS.remove(id);
        save(id, state);
        send(player.getServer(), player.getUuid(), id, state);
        say(player, "짓기를 새로 시작해요. 마을 한가운데를 다시 정해 주세요.", id);
    }

    public static BuildState load(String id) throws IOException {
        var f = buildFile(id);
        BuildState s = Files.exists(f) ? BuildState.fromJson(Files.readString(f, StandardCharsets.UTF_8)) : new BuildState();
        if (s.busy() && !JOBS.containsKey(id)) {
            // The server stopped while building.
            s.status = "stopped";
            if (!"done".equals(s.ground)) {
                s.ground = "stopped";
            }
            for (BuildState.Place p : s.places.values()) {
                if (!"done".equals(p.state) && !"failed".equals(p.state) && !"cleared".equals(p.state)) {
                    p.state = "stopped";
                }
            }
        }
        return s;
    }

    public static void save(String id, BuildState state) {
        try {
            Files.writeString(buildFile(id), state.toJson(), StandardCharsets.UTF_8);
            ScenarioStore.Info info = ScenarioStore.info(id);
            String stage = "done".equals(state.status) ? "built" : info.stage;
            if (!stage.equals(info.stage)) {
                info.stage = stage;
                ScenarioStore.writeInfo(info);
            }
        } catch (IOException e) {
            ScenarioBuilderMod.LOG.warn("Could not save the build state of {}: {}", id, e.toString());
        }
    }

    /** The current state (the running job's if there is one). */
    public static BuildState current(String id) throws IOException {
        Job job = JOBS.get(id);
        return job != null ? job.state : load(id);
    }

    public static boolean canUndo(String id) {
        return SNAPSHOTS.containsKey(id);
    }

    // ------------------------------------------------------------------ where

    /** The keeper stands in the middle of where the town goes. */
    public static void here(ServerPlayerEntity player, String id) {
        try {
            BuildState state = current(id);
            if (state.busy() || hasWork(state)) {
                say(player, "이미 지은 마을이 있어요. 먼저 전체 되돌리기나 장소별 치우기를 하세요.", id);
                return;
            }
            try {
                TownLayout layout = LayoutService.load(id);
                placeAt(state, player.getServerWorld(), player.getBlockPos(), layout == null ? TownLayout.SIZE : layout.size);
            } catch (IllegalStateException e) {
                say(player, "여기에는 지을 땅이 없어요 (너무 낮거나 높아요).", id);
                return;
            }
            save(id, state);
            send(player.getServer(), player.getUuid(), id, state);
            say(player, "여기를 마을 한가운데로 정했어요. 지면 높이 y = " + (state.originY - 1) + ".", id);
        } catch (IOException e) {
            say(player, "정하지 못했어요: " + e.getMessage(), id);
        }
    }

    /** The town's middle at {@code at}, its ground at the surface there. */
    public static void placeAt(BuildState state, ServerWorld world, BlockPos at) {
        placeAt(state, world, at, TownLayout.SIZE);
    }

    /** size: the side of the town's ground (it grows when the lots need it). */
    public static void placeAt(BuildState state, ServerWorld world, BlockPos at, int size) {
        world.getChunk(at.getX() >> 4, at.getZ() >> 4); // the height is only known for a loaded chunk
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        if (y < world.getBottomY() + 2 || y + GroundWork.CLEAR > world.getTopYInclusive()) {
            throw new IllegalStateException("no ground to build on here (y " + y + ")");
        }
        state.placed = true;
        state.originX = at.getX() - size / 2;
        state.originZ = at.getZ() - size / 2;
        state.originY = y;
        state.dimension = world.getRegistryKey().getValue().toString();
        state.message = "";
    }

    private static boolean hasWork(BuildState state) {
        if ("done".equals(state.ground) || "working".equals(state.ground)) {
            return true;
        }
        for (BuildState.Place p : state.places.values()) {
            if ("done".equals(p.state) || "placing".equals(p.state)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ jobs

    private static final class Clear {
        final GroundWork work;
        final Runnable after;

        Clear(GroundWork work, Runnable after) {
            this.work = work;
            this.after = after;
        }
    }

    private static final class Job {
        final String id;
        final ServerWorld world;
        final BuildState state;
        final ScenarioSheet sheet;
        final TownLayout layout;
        final GroundPlan plan;
        final BlockPos origin;
        final UUID owner;
        GroundWork ground;
        /** The ring of land around the town (after the town's ground), or null. */
        BandWork band;
        /** Building stage: the building's walls, floors, doors, stairs and roof (after the ground), or null. */
        ShellWork shell;
        /** Cellars and tunnels under the town (after the ground), or null; their ways down once the buildings stand. */
        UndergroundWork under;
        boolean hatched;
        TownSnapshot capture;
        TownSnapshot restore;
        final Deque<Clear> clears = new ArrayDeque<>();
        final Deque<String> waiting = new ArrayDeque<>();
        int designing;
        final Map<String, AiBuildApi.Design> ready = new LinkedHashMap<>();
        /** Designs that came back well, by place (identical houses reuse them). */
        final Map<String, AiBuildApi.Design> designs = new HashMap<>();
        /** A note for a place's next design (its last one was too small for its lot). */
        final Map<String, String> notes = new HashMap<>();
        /** Places designed again once already because their design was too small. */
        final Set<String> redone = new HashSet<>();
        /** How many times a building was sent back for its stairs or door. */
        final Map<String, Integer> stairTries = new HashMap<>();
        /** Places waiting for the design of the place they are the same as. */
        final Deque<String> later = new ArrayDeque<>();
        final Map<String, UUID> placing = new HashMap<>();
        final Set<Long> forced = new HashSet<>();
        boolean cancelled;
        boolean dirty = true;
        /** How overlapping places were fitted to each other. */
        Overlaps.Result overlaps = new Overlaps.Result();
        int sinceSent;

        Job(String id, ServerWorld world, BuildState state, ScenarioSheet sheet, TownLayout layout, UUID owner) {
            this.id = id;
            this.world = world;
            this.state = state;
            this.sheet = sheet;
            this.layout = layout;
            this.plan = GroundPlan.of(layout, sheet);
            this.origin = new BlockPos(state.originX, state.originY, state.originZ);
            this.owner = owner;
        }

        boolean finished() {
            return capture == null && restore == null && clears.isEmpty() && (ground == null || ground.done())
                && (band == null || band.done()) && (shell == null || shell.done()) && waiting.isEmpty()
                && (under == null || under.done() && (hatched || cancelled))
                && later.isEmpty() && designing == 0 && placing.isEmpty() && (ready.isEmpty() || cancelled);
        }
    }

    private static ServerWorld world(MinecraftServer server, BuildState state) {
        ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(state.dimension)));
        return w != null ? w : server.getOverworld();
    }

    /** Everything the job needs, or a message saying what is missing. */
    private static void say(ServerPlayerEntity player, String text, String id) {
        if (player != null) {
            ScenarioNet.status(player, text, id);
        }
    }

    private static Job prepare(MinecraftServer server, ServerPlayerEntity player, String id) throws IOException {
        if (JOBS.containsKey(id)) {
            say(player, "이미 작업 중이에요.", id);
            return null;
        }
        ScenarioSheet sheet = SheetService.load(id);
        TownLayout layout = LayoutService.load(id);
        BuildState state = load(id);
        if (sheet == null || layout == null) {
            say(player, "먼저 시트와 배치도를 만들어 주세요.", id);
            return null;
        }
        if (!state.placed) {
            say(player, "먼저 마을을 지을 곳을 정해 주세요 (\"내 위치를 한가운데로\").", id);
            return null;
        }
        sheet.normalize();
        layout.check(sheet);
        // Places put over each other are fitted to each other (on this copy of the layout only).
        Overlaps.Result overlaps = Overlaps.resolve(layout, sheet);
        for (String t : overlaps.told) {
            ScenarioBuilderMod.LOG.info("Overlap: {}", t);
        }
        Job job = new Job(id, world(server, state), state, sheet, layout, player == null ? null : player.getUuid());
        job.overlaps = overlaps;
        return job;
    }

    /** Lay the ground (if not yet) and build every place not built yet. */
    public static void start(ServerPlayerEntity player, String id) {
        start(player.getServer(), player, id);
    }

    /** Tests: start without a keeper to tell. */
    public static void startForTests(MinecraftServer server, String id) {
        start(server, null, id);
    }

    public static void clearForTests(MinecraftServer server, String id, String placeId) {
        placeJob(server, null, id, placeId, false);
    }

    public static void undoForTests(MinecraftServer server, String id) {
        undo(server, null, id);
    }

    private static void start(MinecraftServer server, ServerPlayerEntity player, String id) {
        try {
            ScenarioSheet sheet = SheetService.load(id);
            if (sheet != null && (sheet.styleGuide == null || sheet.styleGuide.isBlank()) && !JOBS.containsKey(id)) {
                // Every building follows the town's architecture guide: write it first.
                StyleService.make(server, player, id, guide -> {
                    if (guide != null) {
                        start(server, player, id);
                    }
                });
                return;
            }
            Job job = prepare(server, player, id);
            if (job == null) {
                return;
            }
            BuildState state = job.state;
            state.status = "running";
            state.message = "";
            int ring = 0;
            if (!"done".equals(state.ground)) {
                boolean outer = !"none".equals(job.sheet.outer);
                // Places the keeper put past the map: the ring reaches out to them and lays them a flat lot and a path.
                java.util.List<int[]> pads = new java.util.ArrayList<>();
                for (var s : job.layout.sites) {
                    if (job.layout.outside(s)) {
                        pads.add(new int[] { s.x, s.z, s.w, s.d });
                    }
                }
                OuterBand band = outer ? new OuterBand(job.plan, "closed".equals(job.sheet.outer), OuterBand.WIDTH, pads) : null;
                // Undo keeps the ring and anything past the map the keeper put there.
                ring = Math.max(band == null ? 0 : band.width(), hasOutside(job.layout) ? job.layout.reach() + OuterBand.PAD_MARGIN : 0);
                if (!SNAPSHOTS.containsKey(id)) {
                    // The ring around the town is kept too (its hills and slopes reach higher and lower).
                    int low = Math.max(job.world.getBottomY(), job.origin.getY() - GroundWork.FILL - (outer ? 16 : 0));
                    int high = Math.min(job.world.getTopYInclusive() - job.origin.getY(), GroundWork.CLEAR - 1 + (outer ? 24 : 0));
                    job.capture = new TownSnapshot(job.world, new BlockPos(job.origin.getX() - ring, low, job.origin.getZ() - ring),
                        job.origin.add(job.layout.size - 1 + ring, high, job.layout.size - 1 + ring));
                }
                job.ground = new GroundWork(job.world, job.origin, job.plan, 0, 0, job.layout.size - 1, job.layout.size - 1, job.sheet.period());
                if (job.layout.building()) {
                    job.shell = new ShellWork(job.world, job.origin, job.layout, job.sheet.period());
                }
                if (band != null) {
                    job.band = new BandWork(job.world, job.origin, job.plan, band, job.sheet.period(), java.util.List.of());
                }
                if (!job.layout.building() && com.foredayner.scenario.layout.Underground.any(job.layout, job.sheet)) {
                    job.under = new UndergroundWork(job.world, job.origin, job.layout, job.sheet);
                }
                state.ground = "working";
                state.groundPercent = 0;
            }
            for (ScenarioSheet.Place p : job.sheet.places) {
                BuildState.Place bp = state.place(p.id);
                if (job.layout.site(p.id) == null) {
                    bp.state = "failed";
                    bp.error = "배치도에 자리가 없어요";
                } else if (!"done".equals(bp.state)) {
                    bp.state = "waiting";
                    bp.error = "";
                    job.waiting.add(p.id);
                }
            }
            state.places.keySet().removeIf(k -> job.sheet.place(k) == null);
            begin(job, -ring, -ring, job.layout.size - 1 + ring, job.layout.size - 1 + ring);
            say(player, "짓기 시작했어요: 장소 " + job.waiting.size() + "곳.", id);
        } catch (IOException e) {
            say(player, "시작하지 못했어요: " + e.getMessage(), id);
        }
    }

    /** Clear one place's lot and build it again. */
    public static void rebuild(ServerPlayerEntity player, String id, String placeId) {
        placeJob(player.getServer(), player, id, placeId, true);
    }

    /** Take one place away (its lot goes back to the town's ground). */
    public static void clear(ServerPlayerEntity player, String id, String placeId) {
        placeJob(player.getServer(), player, id, placeId, false);
    }

    private static void placeJob(MinecraftServer server, ServerPlayerEntity player, String id, String placeId, boolean build) {
        try {
            Job job = prepare(server, player, id);
            if (job == null) {
                return;
            }
            TownLayout.Site site = job.layout.site(placeId);
            if (job.sheet.place(placeId) == null) {
                say(player, "그런 장소가 없어요.", id);
                return;
            }
            BuildState.Place bp = job.state.place(placeId);
            job.state.status = "running";
            // Clear where it stood (its old box) and where it goes now.
            List<int[]> rects = new ArrayList<>();
            if (bp.min != null && bp.max != null) {
                rects.add(new int[] { bp.min[0] - job.origin.getX(), bp.min[2] - job.origin.getZ(), bp.max[0] - job.origin.getX(),
                    bp.max[2] - job.origin.getZ() });
            }
            if (site != null) {
                rects.add(new int[] { site.x, site.z, site.x + site.w - 1, site.z + site.d - 1 });
            }
            if (site != null && site.outdoor && !job.overlaps.holes(placeId).isEmpty()) {
                // The buildings standing in this outdoor place stay.
                List<int[]> kept = new ArrayList<>();
                for (int[] r : rects) {
                    kept.addAll(Overlaps.without(r, job.overlaps.holes(placeId)));
                }
                rects = kept;
            }
            String tag = bp.tag;
            if (job.layout.building() && site != null) {
                // A room: empty its inside (the walls, floors and the rooms around stay).
                BlockPos min = roomMin(job, site);
                AiBuildApi.Site inside = roomSite(site);
                for (int x = 0; x < inside.width(); x++) {
                    for (int z = 0; z < inside.depth(); z++) {
                        for (int y = 0; y <= inside.maxHeight(); y++) {
                            BlockPos at = min.add(x, y, z);
                            if (!job.world.getBlockState(at).isAir() && !(job.world.getBlockState(at).getBlock() instanceof net.minecraft.block.StairsBlock)) {
                                job.world.setBlockState(at, net.minecraft.block.Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
                            }
                        }
                    }
                }
                rects.clear();
            }
            for (int i = 0; i < rects.size(); i++) {
                int[] r = rects.get(i);
                boolean last = i == rects.size() - 1;
                job.clears.add(new Clear(new GroundWork(job.world, job.origin, job.plan, r[0], r[1], r[2], r[3], job.sheet.period()), !last ? null : () -> {
                    if (!tag.isEmpty()) {
                        AiBuildApi.removeTagged(job.world, tag);
                    }
                    bp.min = null;
                    bp.max = null;
                    bp.marks.clear();
                    bp.prop = false;
                    if (build && site != null) {
                        bp.state = "waiting";
                        bp.error = "";
                        job.waiting.add(placeId);
                    } else {
                        bp.state = build ? "failed" : "cleared";
                        bp.error = build ? "배치도에 자리가 없어요" : "";
                    }
                    job.dirty = true;
                }));
            }
            if (rects.isEmpty()) {
                job.clears.add(new Clear(new GroundWork(job.world, job.origin, job.plan, 0, 0, -1, -1), () -> {
                    if (!tag.isEmpty()) {
                        AiBuildApi.removeTagged(job.world, tag);
                    }
                    bp.min = null;
                    bp.max = null;
                    bp.marks.clear();
                    if (build && site != null) {
                        bp.state = "waiting";
                        bp.error = "";
                        job.waiting.add(placeId);
                    } else {
                        bp.state = "cleared";
                    }
                    job.dirty = true;
                }));
            }
            int[] all = bounds(rects);
            begin(job, all[0], all[1], all[2], all[3]);
            say(player, build ? "다시 짓는 중이에요." : "치우는 중이에요.", id);
        } catch (IOException e) {
            say(player, "하지 못했어요: " + e.getMessage(), id);
        }
    }

    private static int[] bounds(List<int[]> rects) {
        int[] b = { Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1 };
        for (int[] r : rects) {
            b[0] = Math.min(b[0], r[0]);
            b[1] = Math.min(b[1], r[1]);
            b[2] = Math.max(b[2], r[2]);
            b[3] = Math.max(b[3], r[3]);
        }
        return b;
    }

    /** Stop: no new designs, placing stops, the ground stops where it is. */
    public static void stop(ServerPlayerEntity player, String id) {
        Job job = JOBS.get(id);
        if (job == null) {
            say(player, "진행 중인 작업이 없어요.", id);
            return;
        }
        job.cancelled = true;
        for (String p : job.waiting) {
            job.state.place(p).state = "stopped";
        }
        job.waiting.clear();
        for (String p : job.later) {
            job.state.place(p).state = "stopped";
        }
        job.later.clear();
        for (var e : job.placing.entrySet()) {
            BuildTaskQueue.clear(e.getValue());
            job.state.place(e.getKey()).state = "stopped";
        }
        job.placing.clear();
        for (String p : job.ready.keySet()) {
            job.state.place(p).state = "stopped";
        }
        job.ready.clear();
        job.band = null; // the land around stays as far as it got
        job.under = null;
        if (job.ground != null && !job.ground.done() || job.shell != null && !job.shell.done()) {
            job.ground = null;
            job.shell = null;
            job.state.ground = "stopped";
        }
        job.dirty = true;
        say(player, "멈췄어요. AI가 이미 설계 중인 것은 끝나도 짓지 않아요.", id);
    }

    /** Put the land back as it was before the town (while the server has kept it). */
    public static void undo(ServerPlayerEntity player, String id) {
        undo(player.getServer(), player, id);
    }

    private static void undo(MinecraftServer server, ServerPlayerEntity player, String id) {
        try {
            Job running = JOBS.get(id);
            if (running != null) {
                if ("undoing".equals(running.state.status)) {
                    say(player, "이미 되돌리는 중이에요.", id);
                    return;
                }
                // Building: stop first, then put the land back.
                AFTER.put(id, () -> undo(server, player, id));
                if (player != null) {
                    stop(player, id);
                } else {
                    running.cancelled = true;
                }
                say(player, "멈추는 대로 원래 땅으로 되돌려요.", id);
                return;
            }
            TownSnapshot snapshot = SNAPSHOTS.get(id);
            if (snapshot == null) {
                say(player, "원래 땅의 기록이 없어요 (서버를 다시 켜면 사라져요). 장소별 치우기는 할 수 있어요.", id);
                return;
            }
            BuildState state = load(id);
            Job job = new Job(id, snapshot.world(), state, new ScenarioSheet(), new TownLayout(), player == null ? null : player.getUuid());
            for (BuildState.Place p : state.places.values()) {
                if (!p.tag.isEmpty()) {
                    AiBuildApi.removeTagged(job.world, p.tag);
                }
            }
            job.restore = snapshot;
            state.status = "undoing";
            // Keep the whole saved area loaded while it is put back (the ring and places past the map too).
            begin(job, snapshot.min().getX() - job.origin.getX(), snapshot.min().getZ() - job.origin.getZ(),
                snapshot.max().getX() - job.origin.getX(), snapshot.max().getZ() - job.origin.getZ());
            say(player, "원래 땅으로 되돌리는 중이에요.", id);
        } catch (IOException e) {
            say(player, "되돌리지 못했어요: " + e.getMessage(), id);
        }
    }

    /** Take the keeper to a place's entrance (or the middle of an outdoor place). */
    public static void teleport(ServerPlayerEntity player, String id, String placeId) {
        try {
            BuildState state = current(id);
            TownLayout layout = LayoutService.load(id);
            TownLayout.Site site = layout == null ? null : layout.site(placeId);
            if (!state.placed || site == null) {
                say(player, "그 장소의 자리를 몰라요.", id);
                return;
            }
            int[] at;
            if (site.outdoor) {
                at = new int[] { site.x + site.w / 2, site.z + site.d / 2 };
            } else {
                int[] f = TownLayout.frontPoint(site);
                at = switch (site.front) {
                    case "north" -> new int[] { f[0], f[1] - 1 };
                    case "east" -> new int[] { f[0] + 1, f[1] };
                    case "west" -> new int[] { f[0] - 1, f[1] };
                    default -> new int[] { f[0], f[1] + 1 };
                };
            }
            ServerWorld world = world(player.getServer(), state);
            String face = switch (site.front) {
                case "north" -> "0"; // look south, at the door
                case "east" -> "90";
                case "west" -> "-90";
                default -> "180";
            };
            String cmd = String.format(java.util.Locale.ROOT, "execute in %s run tp %s %d %d %d %s 10", state.dimension,
                player.getGameProfile().getName(), state.originX + at[0], state.originY + 1, state.originZ + at[1], face);
            player.getServer().getCommandManager().executeWithPrefix(
                player.getServer().getCommandSource().withWorld(world).withLevel(4).withSilent(), cmd);
        } catch (IOException e) {
            say(player, "이동하지 못했어요: " + e.getMessage(), id);
        }
    }

    // ------------------------------------------------------------------ running

    private static void begin(Job job, int x0, int z0, int x1, int z1) {
        if (x1 >= x0 && z1 >= z0) {
            for (int cx = (job.origin.getX() + x0) >> 4; cx <= (job.origin.getX() + x1) >> 4; cx++) {
                for (int cz = (job.origin.getZ() + z0) >> 4; cz <= (job.origin.getZ() + z1) >> 4; cz++) {
                    job.world.setChunkForced(cx, cz, true);
                    job.forced.add(((long) cx << 32) ^ (cz & 0xffffffffL));
                }
            }
        }
        JOBS.put(job.id, job);
        save(job.id, job.state);
        send(job.world.getServer(), job.owner, job.id, job.state);
    }

    private static void end(Job job) {
        endJob(job);
        Runnable after = AFTER.remove(job.id);
        if (after != null) {
            after.run();
        }
    }

    private static void endJob(Job job) {
        for (long key : job.forced) {
            job.world.setChunkForced((int) (key >> 32), (int) key, false);
        }
        JOBS.remove(job.id);
        BuildState s = job.state;
        if ("undoing".equals(s.status)) {
            SNAPSHOTS.remove(job.id);
            BuildState fresh = new BuildState();
            fresh.placed = s.placed;
            fresh.originX = s.originX;
            fresh.originY = s.originY;
            fresh.originZ = s.originZ;
            fresh.dimension = s.dimension;
            fresh.message = "원래 땅으로 되돌렸어요.";
            save(job.id, fresh);
            send(job.world.getServer(), job.owner, job.id, fresh);
            message(job, fresh.message);
            return;
        }
        int[] c = s.counts();
        boolean all = c[0] == c[2] && "done".equals(s.ground);
        s.status = job.cancelled ? "stopped" : all ? "done" : "idle";
        s.message = job.cancelled ? "멈췄어요." : all ? "마을을 다 지었어요." : "끝났어요: " + c[0] + "/" + c[2] + "곳 완료"
            + (c[1] > 0 ? ", 실패 " + c[1] + "곳" : "") + ".";
        save(job.id, s);
        send(job.world.getServer(), job.owner, job.id, s);
        message(job, s.message + " (토큰 입력 " + s.tokensIn + " / 출력 " + s.tokensOut + ")");
    }

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        for (Iterator<Job> it = new ArrayList<>(JOBS.values()).iterator(); it.hasNext(); ) {
            Job job = it.next();
            try {
                step(job);
            } catch (RuntimeException e) {
                ScenarioBuilderMod.LOG.error("Town build {} failed", job.id, e);
                job.state.message = "오류: " + e.getMessage();
                job.cancelled = true;
                job.waiting.clear();
                job.later.clear();
                job.ready.clear();
                job.capture = null;
                job.restore = null;
                job.ground = null;
                job.band = null;
                job.shell = null;
                job.under = null;
                job.clears.clear();
            }
            if (job.finished()) {
                end(job);
                continue;
            }
            job.sinceSent++;
            if (job.dirty || job.sinceSent >= 20 && job.ground != null && !job.ground.done()) {
                job.dirty = false;
                job.sinceSent = 0;
                save(job.id, job.state);
                send(server, job.owner, job.id, job.state);
            }
        }
    }

    private static void step(Job job) {
        if (job.capture != null) {
            if (job.capture.capture(columnsPerTick * 2)) {
                SNAPSHOTS.put(job.id, job.capture);
                job.capture = null;
            }
            return;
        }
        if (job.restore != null) {
            if (job.restore.restore(columnsPerTick * 2)) {
                job.restore = null;
            }
            return;
        }
        if (!job.clears.isEmpty()) {
            Clear c = job.clears.peek();
            c.work.step(columnsPerTick);
            if (c.work.done()) {
                job.clears.poll();
                if (c.after != null) {
                    c.after.run();
                }
                job.dirty = true;
            }
            return;
        }
        if (job.ground != null && !job.ground.done()) {
            job.ground.step(columnsPerTick);
            job.state.groundPercent = job.ground.percent();
            if (job.ground.done() && job.shell == null) {
                job.state.ground = "done";
                job.dirty = true;
            }
        } else {
            if (job.shell != null && !job.shell.done()) {
                // Building stage: the building's shell by code, before its rooms are furnished.
                job.shell.step(columnsPerTick * 4);
                if (job.shell.done()) {
                    job.state.ground = "done";
                    job.dirty = true;
                }
            }
            if (job.under != null && !job.under.done() && !job.cancelled) {
                // Cellars and tunnels, while the buildings go up (they are all below the ground).
                job.under.step(columnsPerTick * 16);
                if (job.under.done()) {
                    for (String note : job.under.notes()) {
                        message(job, note);
                    }
                    job.dirty = true;
                }
            }
            if (job.band != null && !job.band.done() && !job.cancelled) {
                // The land around the town, while the buildings go up.
                job.band.step(columnsPerTick);
                if (job.band.done()) {
                    job.dirty = true;
                }
            }
        }
        for (int n = job.later.size(); n > 0 && !job.cancelled; n--) {
            design(job, job.later.poll()); // goes back on the list while its model is still being designed
        }
        while (!job.cancelled && job.designing < parallel && !job.waiting.isEmpty()) {
            design(job, job.waiting.poll());
        }
        boolean groundReady = (job.ground == null || job.ground.done()) && (job.shell == null || job.shell.done());
        if (groundReady && !job.cancelled && !job.ready.isEmpty()) {
            for (var e : new ArrayList<>(job.ready.entrySet())) {
                job.ready.remove(e.getKey());
                place(job, e.getKey(), e.getValue());
            }
        }
        if (job.under != null && job.under.done() && !job.hatched && !job.cancelled && job.waiting.isEmpty() && job.later.isEmpty()
            && job.designing == 0 && job.placing.isEmpty() && job.ready.isEmpty()) {
            // The buildings stand: each cellar's trapdoor and ladder where there is room to stand.
            job.under.hatches();
            job.hatched = true;
            message(job, "지하실과 통로를 지었어요.");
        }
    }

    private static String tag(String id, String placeId) {
        return "scn_" + Integer.toHexString((id + "/" + placeId).hashCode());
    }

    private static BlockPos siteMin(Job job, TownLayout.Site s) {
        return job.origin.add(s.x, 0, s.z);
    }

    /** Building stage: the first block inside a room, on its floor. */
    private static BlockPos roomMin(Job job, TownLayout.Site s) {
        return job.origin.add(s.x + 1, s.level * com.foredayner.scenario.layout.IndoorPlan.FLOOR_H, s.z + 1);
    }

    private static AiBuildApi.Site roomSite(TownLayout.Site s) {
        return new AiBuildApi.Site(Math.max(1, s.w - 2), Math.max(1, s.d - 2), com.foredayner.scenario.layout.IndoorPlan.FLOOR_H - 2);
    }

    /** The side of a room its (first) door is in: its furnishing keeps that side's middle clear. */
    static String roomFront(TownLayout l, TownLayout.Site s) {
        for (TownLayout.Door d : l.doors) {
            if (d.level != s.level) {
                continue;
            }
            boolean inX = d.x > s.x && d.x < s.x + s.w - 1;
            boolean inZ = d.z > s.z && d.z < s.z + s.d - 1;
            if (inX && d.z == s.z) {
                return "north";
            }
            if (inX && d.z == s.z + s.d - 1) {
                return "south";
            }
            if (inZ && d.x == s.x) {
                return "west";
            }
            if (inZ && d.x == s.x + s.w - 1) {
                return "east";
            }
        }
        return "south";
    }

    private static void design(Job job, String placeId) {
        ScenarioSheet.Place p = job.sheet.place(placeId);
        TownLayout.Site s = job.layout.site(placeId);
        BuildState.Place bp = job.state.place(placeId);
        if (p == null || s == null) {
            bp.state = "failed";
            bp.error = "배치도에 자리가 없어요";
            job.dirty = true;
            return;
        }
        if (s.outdoor && "pine".equals(p.kind)) {
            // The ground work grows the tree itself; nothing to design.
            bp.state = "done";
            bp.error = "";
            bp.min = new int[] { job.origin.getX() + s.x, job.origin.getY(), job.origin.getZ() + s.z };
            bp.max = new int[] { job.origin.getX() + s.x + s.w - 1, job.origin.getY() + 18, job.origin.getZ() + s.z + s.d - 1 };
            job.dirty = true;
            return;
        }
        ScenarioSheet.Place model0 = p.sameAs.isEmpty() ? null : job.sheet.place(p.sameAs);
        if (model0 != null && !s.outdoor && !model0.outdoor) {
            // The same house as another place: wait for that design and place it again here.
            AiBuildApi.Design same = job.designs.get(model0.id);
            String other = job.state.place(model0.id).state;
            boolean pending = "waiting".equals(other) || "designing".equals(other);
            TownLayout.Site modelSite = job.layout.site(model0.id);
            if (same != null && modelSite != null && modelSite.w == s.w && modelSite.d == s.d) {
                bp.state = "placing";
                bp.error = "";
                job.ready.put(placeId, same);
                job.dirty = true;
                return;
            }
            if (pending) {
                bp.state = "waiting";
                job.later.add(placeId);
                return;
            }
            // Its model failed or was built before this run: design this one on its own.
        }
        bp.state = "designing";
        bp.error = "";
        job.designing++;
        job.dirty = true;
        String model = ScenarioConfig.buildingModel();
        CompletableFuture<AiBuildApi.Design> future;
        if (job.layout.building()) {
            // A room of the building stage: its furnishing, inside walls built by code.
            AiBuildApi.BuildingRequest req = new AiBuildApi.BuildingRequest(BuildPrompts.roomDescription(job.sheet, p), BuildPrompts.style(job.sheet),
                List.of(), BuildPrompts.marks(job.sheet, p), BuildPrompts.ROOM_EXTRA + blocksOnly(job.sheet), model,
                AiBuildApi.propEraFor(job.sheet.period()), true);
            future = designer.building(job.world, roomMin(job, s), roomSite(s), req);
        } else if (s.outdoor) {
            // A whole scene of blocks over the lot, ground included (never display entities).
            int[] size = Overlaps.designSize(s);
            AiBuildApi.Site whole = new AiBuildApi.Site(size[0], size[1], BuildPrompts.outdoorHeight(p));
            AiBuildApi.BuildingRequest req = new AiBuildApi.BuildingRequest(BuildPrompts.propDescription(job.sheet, p), BuildPrompts.style(job.sheet),
                List.of(), BuildPrompts.marks(job.sheet, p), BuildPrompts.propExtra(p) + Overlaps.note(job.overlaps, s) + job.notes.getOrDefault(placeId, "")
                    + blocksOnly(job.sheet) + "\n" + AiBuildApi.TRIM_TO_SITE, model);
            future = designer.building(job.world, siteMin(job, s), whole, req);
        } else {
            int[] size = Overlaps.designSize(s);
            AiBuildApi.Site site = new AiBuildApi.Site(size[0], size[1], BuildPrompts.maxHeight(p));
            AiBuildApi.BuildingRequest req = new AiBuildApi.BuildingRequest(BuildPrompts.description(job.sheet, p), BuildPrompts.style(job.sheet),
                BuildPrompts.rooms(job.sheet, p), BuildPrompts.marks(job.sheet, p),
                BuildPrompts.extra(job.sheet, p, size[0], size[1]) + Overlaps.note(job.overlaps, s) + cellarStairsNote(job, s)
                    + job.notes.getOrDefault(placeId, "") + blocksOnly(job.sheet), model,
                AiBuildApi.propEraFor(job.sheet.period()), false);
            future = designer.building(job.world, siteMin(job, s), site, req);
        }
        future.whenComplete((design, error) -> job.world.getServer().execute(() -> {
            job.designing--;
            job.dirty = true;
            if (design != null) {
                job.state.tokensIn += design.inputTokens();
                job.state.tokensOut += design.outputTokens();
            }
            if (job.cancelled) {
                bp.state = "stopped";
                return;
            }
            if (error != null || design == null || !design.ok()) {
                String why = error != null ? String.valueOf(error.getMessage()) : design == null ? "no answer" : design.error();
                if (s.outdoor) {
                    // The ground of an outdoor place is already made; it only lacks its prop.
                    bp.state = "done";
                    bp.error = "소품 없음: " + short80(why);
                    bp.min = new int[] { job.origin.getX() + s.x, job.origin.getY(), job.origin.getZ() + s.z };
                    bp.max = new int[] { job.origin.getX() + s.x + s.w - 1, job.origin.getY(), job.origin.getZ() + s.z + s.d - 1 };
                } else {
                    bp.state = "failed";
                    bp.error = short80(why);
                }
                return;
            }
            if (!job.layout.building() && (s.outdoor || ScenarioSheet.named(p)) && !job.redone.contains(placeId)) {
                // A scenario building or outdoor scene that leaves most of its big lot empty: once more, bigger.
                int[] used = usedSize(design.plan());
                int[] room = Overlaps.designSize(s);
                if (used[0] < room[0] * 0.6 || used[1] < room[1] * 0.6) {
                    job.redone.add(placeId);
                    job.notes.put(placeId, String.format(java.util.Locale.ROOT, "\nYOUR LAST DESIGN WAS ONLY %d x %d ON THIS %d x %d SITE:"
                        + (s.outdoor ? " spread the scene over the whole site." : " make the building bigger and use most of the site."),
                        used[0], used[1], room[0], room[1]));
                    bp.state = "waiting";
                    job.waiting.addFirst(placeId);
                    return;
                }
            }
            if (!s.outdoor && !job.layout.building() && !checkStairs(job, s, p, placeId, bp, design.plan())) {
                return; // sent back to the AI
            }
            bp.state = "placing";
            job.designs.put(placeId, design);
            job.ready.put(placeId, design);
        }));
    }

    /** Where the stairs into this building's cellar go (its lot coordinates), or null. */
    static Underground.Stairwell cellarStairs(Job job, TownLayout.Site s) {
        for (Underground.Cellar c : Underground.cellars(job.layout, job.sheet)) {
            if (c.id().equals(s.id)) {
                return Underground.stairwell(c, s.w, s.d);
            }
        }
        return null;
    }

    /** The cellar stairs' cells on the ground floor, in the design's frame: {x1, z1, x2, z2}, or null. */
    static int[] cellarStairsInDesign(Job job, TownLayout.Site s) {
        Underground.Stairwell sw = cellarStairs(job, s);
        if (sw == null) {
            return null;
        }
        int[] r = sw.reserved();
        return Overlaps.toDesign(s, new Overlaps.Rect(r[0], r[1], r[2], r[3]));
    }

    /** What the AI is told about the cellar stairs' place on its ground floor. */
    static String cellarStairsNote(Job job, TownLayout.Site s) {
        Underground.Stairwell sw = cellarStairs(job, s);
        int[] d = cellarStairsInDesign(job, s);
        if (sw == null || d == null) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "\nSTAIRS DOWN TO THE CELLAR go in at x %d..%d, z %d..%d on the ground floor (a straight flight"
            + " %d block%s wide with a railing round it, cut in by code): keep those cells free of walls, furniture and everything"
            + " else from y = 1 up to the ceiling, and keep the floor at y = 0 there. They stand inside a room or the hall, with a"
            + " way to them from the door.", d[0], d[2], d[1], d[3], sw.width(), sw.width() > 1 ? "s" : "");
    }

    /**
     * A building's design: the cellar stairs' cells cut free, then the walk check (a door in, and
     * stairs to every floor). Sends it back to the AI (twice at most) with what is wrong; after
     * that puts in stairs itself where it can. False when it was sent back.
     */
    private static boolean checkStairs(Job job, TownLayout.Site s, ScenarioSheet.Place p, String placeId, BuildState.Place bp,
                                       com.example.ai.build.BuildPlan plan) {
        int[] hole = cellarStairsInDesign(job, s);
        if (hole != null) {
            Overlaps.carve(plan, hole[0], hole[1], hole[2], hole[3], 1, StairCheck.STOREY - 1);
        }
        int[] size = Overlaps.designSize(s);
        int high = BuildPrompts.maxHeight(p);
        int floors = Math.max(1, p.floors);
        int width = Underground.stairWidth(s.w, s.d);
        String problem = StairCheck.problem(plan, size[0], size[1], high, floors, width);
        if (problem == null) {
            return true;
        }
        int tries = job.stairTries.merge(placeId, 1, Integer::sum);
        if (tries <= 2) {
            job.notes.put(placeId, "\nYOUR LAST DESIGN FAILED THIS CHECK, FIX IT:\n" + problem + "\n");
            bp.state = "waiting";
            job.waiting.addFirst(placeId);
            return false;
        }
        int added = StairCheck.addStairs(plan, size[0], size[1], high, floors, width, stairsFor(job.sheet.period()));
        String still = StairCheck.problem(plan, size[0], size[1], high, floors, width);
        if (added > 0) {
            message(job, p.name + ": 위층 계단이 없어 직접 놓았어요.");
        }
        if (still != null) {
            message(job, p.name + ": 오가는 길(출입문·계단)을 확인하지 못했어요. 지은 뒤 살펴봐 주세요.");
            ScenarioBuilderMod.LOG.info("{}: {}", placeId, still);
        }
        return true;
    }

    private static String stairsFor(String period) {
        return Period.medieval(period) ? "minecraft:spruce_stairs" : Period.contemporary(period) ? "minecraft:stone_brick_stairs"
            : "minecraft:oak_stairs";
    }

    /** Width and depth of the blocks a design places (x and z extent). */
    static int[] usedSize(com.example.ai.build.BuildPlan plan) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (com.example.ai.build.BuildPlan.Op op : plan.ops) {
            if (op.isAir()) {
                continue;
            }
            minX = Math.min(minX, Math.min(op.x1, op.x2));
            maxX = Math.max(maxX, Math.max(op.x1, op.x2));
            minZ = Math.min(minZ, Math.min(op.z1, op.z2));
            maxZ = Math.max(maxZ, Math.max(op.z1, op.z2));
        }
        return minX > maxX ? new int[] { 0, 0 } : new int[] { maxX - minX + 1, maxZ - minZ + 1 };
    }

    private static String short80(String s) {
        String one = s == null ? "" : s.lines().findFirst().orElse("");
        return one.length() > 80 ? one.substring(0, 80) + "…" : one;
    }

    private static void place(Job job, String placeId, AiBuildApi.Design design) {
        TownLayout.Site s = job.layout.site(placeId);
        BuildState.Place bp = job.state.place(placeId);
        String tag = tag(job.id, placeId);
        UUID lane = UUID.randomUUID();
        bp.tag = tag;
        job.placing.put(placeId, lane);
        ScenarioSheet.Place place0 = job.sheet.place(placeId);
        boolean house = !job.layout.building() && !s.outdoor && place0 != null && !"ruins".equals(place0.kind);
        boolean indoors = !s.outdoor && place0 != null;
        BlockPos lotMin = indoors ? siteMin(job, s) : null;
        BlockPos lotMax = indoors ? lotMin.add(s.w - 1, BuildPrompts.maxHeight(place0), s.d - 1) : null;
        Runnable done = () -> job.world.getServer().execute(() -> {
            if (job.placing.remove(placeId) != null) {
                if (indoors) {
                    // Dark spots inside: invisible light blocks.
                    int lit = Lights.fill(job.world, lotMin, lotMax,
                        "ruins".equals(place0.kind) || Outdoors.worn(place0, job.sheet) ? Lights.DIM : Lights.BRIGHT);
                    ScenarioBuilderMod.LOG.info("Lights in {}: {}", placeId, lit);
                }
                if (house) {
                    // Every way in: a double door with free ground in front of and behind it.
                    int found = Doors.fix(job.world, lotMin, lotMax);
                    ScenarioBuilderMod.LOG.info("Doors of {}: {} way(s) in", placeId, found);
                }
                bp.state = "done";
                job.dirty = true;
            }
        });
        if (job.layout.building()) {
            AiBuildApi.Placed placed = AiBuildApi.placeBuilding(job.world, design.plan(), roomMin(job, s), roomSite(s),
                roomFront(job.layout, s), tag, lane, done);
            bp.min = new int[] { placed.min().getX(), placed.min().getY(), placed.min().getZ() };
            bp.max = new int[] { placed.max().getX(), placed.max().getY(), placed.max().getZ() };
            bp.marks.clear();
            for (var e : placed.marks().entrySet()) {
                List<int[]> list = new ArrayList<>();
                for (BlockPos pos : e.getValue()) {
                    list.add(new int[] { pos.getX(), pos.getY(), pos.getZ() });
                }
                bp.marks.put(e.getKey(), list);
            }
        } else if (s.outdoor) {
            ScenarioSheet.Place p = job.sheet.place(placeId);
            int high = BuildPrompts.outdoorHeight(p);
            int cut = Overlaps.carve(design.plan(), s, job.overlaps);
            if (cut > 0) {
                ScenarioBuilderMod.LOG.info("Cut {} commands out of {} where other places stand", cut, placeId);
            }
            int[] size = Overlaps.designSize(s);
            AiBuildApi.Placed placed = AiBuildApi.placeBuilding(job.world, design.plan(), siteMin(job, s),
                new AiBuildApi.Site(size[0], size[1], high), s.front, tag, lane, done);
            bp.prop = true;
            bp.min = new int[] { job.origin.getX() + s.x, job.origin.getY() - 2, job.origin.getZ() + s.z };
            bp.max = new int[] { job.origin.getX() + s.x + s.w - 1, job.origin.getY() + high + 2, job.origin.getZ() + s.z + s.d - 1 };
            bp.marks.clear();
            for (var e : placed.marks().entrySet()) {
                List<int[]> list = new ArrayList<>();
                for (BlockPos pos : e.getValue()) {
                    list.add(new int[] { pos.getX(), pos.getY(), pos.getZ() });
                }
                bp.marks.put(e.getKey(), list);
            }
        } else {
            ScenarioSheet.Place p = job.sheet.place(placeId);
            int[] size = Overlaps.designSize(s);
            AiBuildApi.Site site = new AiBuildApi.Site(size[0], size[1], BuildPrompts.maxHeight(p));
            AiBuildApi.Placed placed = AiBuildApi.placeBuilding(job.world, design.plan(), siteMin(job, s), site, s.front, tag, lane, done);
            bp.min = new int[] { placed.min().getX(), placed.min().getY(), placed.min().getZ() };
            bp.max = new int[] { placed.max().getX(), placed.max().getY(), placed.max().getZ() };
            bp.marks.clear();
            for (var e : placed.marks().entrySet()) {
                List<int[]> list = new ArrayList<>();
                for (BlockPos pos : e.getValue()) {
                    list.add(new int[] { pos.getX(), pos.getY(), pos.getZ() });
                }
                bp.marks.put(e.getKey(), list);
            }
        }
        job.dirty = true;
    }

    // ------------------------------------------------------------------ telling the keeper

    private static void send(MinecraftServer server, UUID owner, String id, BuildState state) {
        ServerPlayerEntity player = owner == null ? null : server.getPlayerManager().getPlayer(owner);
        if (player != null) {
            ScenarioNet.build(player, id, state, canUndo(id));
        }
    }

    private static void message(Job job, String text) {
        ServerPlayerEntity player = job.owner == null ? null : job.world.getServer().getPlayerManager().getPlayer(job.owner);
        if (player != null) {
            say(player, text, job.id);
        }
    }

    /** Tests: wait for nothing to be running for a scenario. */
    public static boolean idle(String id) {
        return !JOBS.containsKey(id);
    }

    /** Server stopping: forget jobs (their state on disk says stopped when read again). */
    public static void reset() {
        JOBS.clear();
        SNAPSHOTS.clear();
        AFTER.clear();
    }

    /** 소품: 안함 — the AI Build Assistant keeps the design to blocks (it checks and asks again). */
    static String blocksOnly(ScenarioSheet sheet) {
        return "\n" + AiBuildApi.BLOCKS_ONLY; // the town is always blocks only: it stays whole without the mods
    }

}
