package com.foredayner.scenario.town;

import com.example.ai.build.BuildTaskQueue;
import com.example.api.AiBuildApi;
import com.foredayner.scenario.ScenarioBuilderMod;
import com.foredayner.scenario.layout.LayoutService;
import com.foredayner.scenario.layout.TownLayout;
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
            return AiBuildApi.designBuilding(world, near, site, req, line -> ScenarioBuilderMod.LOG.debug("{}", line));
        }

        @Override
        public CompletableFuture<AiBuildApi.Design> object(ServerWorld world, BlockPos near, String description, double maxSize, String model) {
            return AiBuildApi.designObject(world, near, description, maxSize, model, line -> ScenarioBuilderMod.LOG.debug("{}", line));
        }
    };

    public static Designer designer = AI;
    /** Designs asked for at the same time. */
    public static int parallel = 3;
    /** Ground columns per tick. */
    public static int columnsPerTick = 300;
    public static final double PROP_SIZE = 4.0;

    private static final Map<String, Job> JOBS = new HashMap<>();
    /** The land as it was before each town (memory only). */
    private static final Map<String, TownSnapshot> SNAPSHOTS = new HashMap<>();

    private BuildService() {
    }

    // ------------------------------------------------------------------ state on disk

    public static BuildState load(String id) throws IOException {
        var f = ScenarioStore.dir(id).resolve("build.json");
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
            Files.writeString(ScenarioStore.dir(id).resolve("build.json"), state.toJson(), StandardCharsets.UTF_8);
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
                placeAt(state, player.getServerWorld(), player.getBlockPos());
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
        world.getChunk(at.getX() >> 4, at.getZ() >> 4); // the height is only known for a loaded chunk
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        if (y < world.getBottomY() + 2 || y + GroundWork.CLEAR > world.getTopYInclusive()) {
            throw new IllegalStateException("no ground to build on here (y " + y + ")");
        }
        state.placed = true;
        state.originX = at.getX() - TownLayout.SIZE / 2;
        state.originZ = at.getZ() - TownLayout.SIZE / 2;
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
        TownSnapshot capture;
        TownSnapshot restore;
        final Deque<Clear> clears = new ArrayDeque<>();
        final Deque<String> waiting = new ArrayDeque<>();
        int designing;
        final Map<String, AiBuildApi.Design> ready = new LinkedHashMap<>();
        final Map<String, UUID> placing = new HashMap<>();
        final Set<Long> forced = new HashSet<>();
        boolean cancelled;
        boolean dirty = true;
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
            return capture == null && restore == null && clears.isEmpty() && (ground == null || ground.done()) && waiting.isEmpty()
                && designing == 0 && placing.isEmpty() && (ready.isEmpty() || cancelled);
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
        return new Job(id, world(server, state), state, sheet, layout, player == null ? null : player.getUuid());
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
            if (!"done".equals(state.ground)) {
                if (!SNAPSHOTS.containsKey(id)) {
                    int low = Math.max(job.world.getBottomY(), job.origin.getY() - GroundWork.FILL);
                    job.capture = new TownSnapshot(job.world, new BlockPos(job.origin.getX(), low, job.origin.getZ()),
                        job.origin.add(TownLayout.SIZE - 1, GroundWork.CLEAR - 1, TownLayout.SIZE - 1));
                }
                job.ground = new GroundWork(job.world, job.origin, job.plan, 0, 0, TownLayout.SIZE - 1, TownLayout.SIZE - 1);
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
            begin(job, 0, 0, TownLayout.SIZE - 1, TownLayout.SIZE - 1);
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
            String tag = bp.tag;
            for (int i = 0; i < rects.size(); i++) {
                int[] r = rects.get(i);
                boolean last = i == rects.size() - 1;
                job.clears.add(new Clear(new GroundWork(job.world, job.origin, job.plan, r[0], r[1], r[2], r[3]), !last ? null : () -> {
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
                job.clears.add(new Clear(new GroundWork(job.world, job.origin, job.plan, 0, 0, -1, -1), () -> bp.state = "cleared"));
            }
            int[] all = bounds(rects);
            begin(job, all[0], all[1], all[2], all[3]);
            say(player, build ? "다시 짓는 중이에요." : "치우는 중이에요.", id);
        } catch (IOException e) {
            say(player, "하지 못했어요: " + e.getMessage(), id);
        }
    }

    private static int[] bounds(List<int[]> rects) {
        int[] b = { TownLayout.SIZE, TownLayout.SIZE, -1, -1 };
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
        for (var e : job.placing.entrySet()) {
            BuildTaskQueue.clear(e.getValue());
            job.state.place(e.getKey()).state = "stopped";
        }
        job.placing.clear();
        for (String p : job.ready.keySet()) {
            job.state.place(p).state = "stopped";
        }
        job.ready.clear();
        if (job.ground != null && !job.ground.done()) {
            job.ground = null;
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
            if (JOBS.containsKey(id)) {
                say(player, "작업 중에는 되돌릴 수 없어요. 먼저 멈추세요.", id);
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
            begin(job, 0, 0, TownLayout.SIZE - 1, TownLayout.SIZE - 1);
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
                job.ready.clear();
                job.capture = null;
                job.restore = null;
                job.ground = null;
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
            if (job.ground.done()) {
                job.state.ground = "done";
                job.dirty = true;
            }
        }
        while (!job.cancelled && job.designing < parallel && !job.waiting.isEmpty()) {
            design(job, job.waiting.poll());
        }
        boolean groundReady = job.ground == null || job.ground.done();
        if (groundReady && !job.cancelled && !job.ready.isEmpty()) {
            for (var e : new ArrayList<>(job.ready.entrySet())) {
                job.ready.remove(e.getKey());
                place(job, e.getKey(), e.getValue());
            }
        }
    }

    private static String tag(String id, String placeId) {
        return "scn_" + Integer.toHexString((id + "/" + placeId).hashCode());
    }

    private static BlockPos siteMin(Job job, TownLayout.Site s) {
        return job.origin.add(s.x, 0, s.z);
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
        bp.state = "designing";
        bp.error = "";
        job.designing++;
        job.dirty = true;
        String model = ScenarioConfig.model(ScenarioConfig.get().buildingModel);
        CompletableFuture<AiBuildApi.Design> future;
        if (s.outdoor) {
            future = designer.object(job.world, siteMin(job, s), BuildPrompts.prop(job.sheet, p), PROP_SIZE, model);
        } else {
            AiBuildApi.Site site = new AiBuildApi.Site(s.w, s.d, BuildPrompts.maxHeight(p));
            AiBuildApi.BuildingRequest req = new AiBuildApi.BuildingRequest(BuildPrompts.description(job.sheet, p), BuildPrompts.style(job.sheet),
                BuildPrompts.rooms(p), BuildPrompts.marks(job.sheet, p), BuildPrompts.extra(job.sheet, p, s.w), model);
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
            bp.state = "placing";
            job.ready.put(placeId, design);
        }));
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
        Runnable done = () -> job.world.getServer().execute(() -> {
            if (job.placing.remove(placeId) != null) {
                bp.state = "done";
                job.dirty = true;
            }
        });
        if (s.outdoor) {
            BlockPos onTop = job.origin.add(s.x + s.w / 2, -1, s.z + s.d / 2);
            AiBuildApi.placeObject(job.world, design.plan(), onTop, s.front, tag, lane);
            bp.prop = true;
            bp.min = new int[] { job.origin.getX() + s.x, job.origin.getY(), job.origin.getZ() + s.z };
            bp.max = new int[] { job.origin.getX() + s.x + s.w - 1, job.origin.getY() + (int) Math.ceil(PROP_SIZE), job.origin.getZ() + s.z + s.d - 1 };
            BuildTaskQueue.whenEmpty(lane, done);
        } else {
            ScenarioSheet.Place p = job.sheet.place(placeId);
            AiBuildApi.Site site = new AiBuildApi.Site(s.w, s.d, BuildPrompts.maxHeight(p));
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
    }
}
