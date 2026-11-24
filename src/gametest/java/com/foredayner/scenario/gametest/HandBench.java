package com.foredayner.scenario.gametest;

import com.foredayner.scenario.layout.IndoorPlan;
import com.foredayner.scenario.layout.LayoutService;
import com.foredayner.scenario.layout.TownLayout;
import com.foredayner.scenario.sheet.ScenarioSheet;
import com.foredayner.scenario.sheet.SheetService;
import com.foredayner.scenario.store.ScenarioStore;
import com.foredayner.scenario.town.BuildPrompts;
import com.foredayner.scenario.town.BuildService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The whole way from a scenario to a built stage with no AI: the sheet, the floor plan and every
 * room's furnishing are written by hand as the AI would answer them ({@code <name>.sheet.json},
 * {@code <name>.plan.json}, {@link HandDesigner}); the builder does the rest as in a real run. Then
 * checks what came out (stairs, doors, the false wall, clue spots, nothing in front of doors) and
 * photographs it. Results in hand/.
 * <p>
 * Runs only when SCN_HAND names the scenario (the "hand" workflow).
 */
public class HandBench implements FabricClientGameTest {

    private static final Path OUT = Path.of("hand");

    private static void write(String file, String text) {
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve(file), text, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        String name = System.getenv().getOrDefault("SCN_HAND", "").trim();
        if (name.isEmpty()) {
            return;
        }
        HandDesigner hand = new HandDesigner();
        // Real terrain (a flat world lies just over the bottom of the world: no room for the cellar).
        try (TestSingleplayerContext game = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(creator -> {
                    creator.setWorldType(creator.getNormalWorldTypes().get(0));
                    creator.setSeed(name);
                    creator.setGameMode(WorldCreator.Mode.CREATIVE);
                })
                .create()) {
            game.getClientWorld().waitForChunksRender();
            for (String c : new String[] { "time set noon", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                "gamerule doMobSpawning false", "gamerule sendCommandFeedback false" }) {
                game.getServer().runCommand(c);
            }
            String id = game.getServer().computeOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().get(0);
                server.getPlayerManager().getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 4, false));
                try {
                    ScenarioStore.Info info = ScenarioStore.save(name, ScenarioGameTests.resource("/scenarios/" + name + ".txt"), false);
                    ScenarioSheet sheet = ScenarioSheet.fromJson(ScenarioGameTests.resource("/scenarios/" + name + ".sheet.json"));
                    List<String> sheetProblems = sheet.normalize();
                    write("sheet_problems.txt", String.join("\n", sheetProblems) + "\n");
                    SheetService.save(info.id, sheet);
                    TownLayout layout;
                    List<String> repaired;
                    if (sheet.building()) {
                        layout = IndoorPlan.fromAnswer(ScenarioGameTests.resource("/scenarios/" + name + ".plan.json"), sheet);
                        repaired = IndoorPlan.repair(layout, sheet);
                    } else {
                        // A town: the layout the code makes (the AI only touches it up), then the scenario's own moves.
                        layout = TownLayout.auto(sheet);
                        repaired = adjust(layout, name);
                    }
                    List<String> problems = layout.check(sheet);
                    write("plan_check.txt", "repaired: " + repaired + "\nproblems: " + problems + "\n");
                    LayoutService.save(info.id, layout);
                    write("layout.json", layout.toJson());
                    com.example.config.AiBuilderConfig.get().ticksPerBlock = 1;
                    BuildService.designer = hand;
                    BuildService.columnsPerTick = 1500;
                    BuildService.here(player, info.id);
                    BuildService.start(player, info.id);
                    return info.id;
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            for (int i = 0; i < 20 * 60; i++) {
                context.waitTicks(20);
                if (game.getServer().computeOnServer(server -> BuildService.idle(id))) {
                    break;
                }
            }
            context.waitTicks(40);
            List<String> checks = new ArrayList<>();
            int[] origin = game.getServer().computeOnServer(server -> {
                try {
                    var state = BuildService.load(id);
                    write("build.json", state.toJson());
                    var f = BuildService.cluesFile(id);
                    write("clues.json", Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "no clues file");
                    StringBuilder sc = new StringBuilder();
                    hand.scripts.forEach((room, script) -> sc.append("== ").append(room).append('\n').append(script).append('\n'));
                    write("scripts.txt", sc.toString());
                    checks.addAll(check(server.getOverworld(), id, state));
                    write("checks.txt", String.join("\n", checks) + "\n");
                    write("playtest.json", playtest(server.getOverworld(), id, state));
                    return new int[] { state.originX, state.originY, state.originZ };
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            TownLayout layout = game.getServer().computeOnServer(server -> {
                try {
                    return LayoutService.load(id);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
            if (layout.building()) {
                photos(context, game, layout, origin);
            } else {
                ScenarioSheet sheetNow = game.getServer().computeOnServer(server -> {
                    try {
                        return SheetService.load(id);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                townPhotos(context, game, layout, origin, sheetNow);
            }
            clueShots(context, game);
            game.getServer().runCommand("gamemode creative @a");
            long failed = checks.stream().filter(c -> c.startsWith("FAIL")).count();
            if (failed > 0) {
                throw new AssertionError(failed + " check(s) failed:\n" + String.join("\n", checks));
            }
        }
    }

    // ------------------------------------------------------------------ a town

    /**
     * Moves the scenario asks of the town layout ({@code <name>.adjust.json}): {"join": [[a, b], ...]}
     * puts place a against the back of place b, their lots overlapping, so the two are built joined.
     */
    private static List<String> adjust(TownLayout l, String name) {
        List<String> notes = new ArrayList<>();
        String text;
        try {
            text = ScenarioGameTests.resource("/scenarios/" + name + ".adjust.json");
        } catch (RuntimeException e) {
            return notes;
        }
        if (text == null || text.isBlank()) {
            return notes;
        }
        var json = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
        if (json.has("join")) {
            for (var el : json.getAsJsonArray("join")) {
                var pair = el.getAsJsonArray();
                TownLayout.Site a = l.site(pair.get(0).getAsString()), b = l.site(pair.get(1).getAsString());
                if (a == null || b == null) {
                    continue;
                }
                int over = 5; // lots overlap this much: the buildings on them meet
                switch (b.front == null ? "south" : b.front) {
                    case "north" -> { a.z = b.z + b.d - over; a.x = b.x + (b.w - a.w) / 2; }
                    case "east" -> { a.x = b.x - a.w + over; a.z = b.z + (b.d - a.d) / 2; }
                    case "west" -> { a.x = b.x + b.w - over; a.z = b.z + (b.d - a.d) / 2; }
                    default -> { a.z = b.z - a.d + over; a.x = b.x + (b.w - a.w) / 2; }
                }
                // Back to back: a's door faces away from b.
                a.front = switch (b.front == null ? "south" : b.front) {
                    case "north" -> "south";
                    case "east" -> "west";
                    case "west" -> "east";
                    default -> "north";
                };
                notes.add(a.id + " joined to the back of " + b.id + " at " + a.x + "," + a.z);
            }
        }
        return notes;
    }

    /** A door in a building (its lower half), whether it is one block wide, and the way out of it (null: an inner door). */
    record Door(BlockPos at, boolean single, net.minecraft.util.math.Direction out) {
    }

    private static boolean overhead(ServerWorld world, BlockPos p) {
        for (int y = 2; y <= 16; y++) {
            BlockPos q = p.up(y);
            if (!world.getBlockState(q).getCollisionShape(world, q).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Open sky a few blocks out from a door this way (past eaves), not through a wall. */
    private static boolean skyOut(ServerWorld world, BlockPos door, net.minecraft.util.math.Direction dir) {
        for (int k = 1; k <= 4; k++) {
            BlockPos at = door.offset(dir, k);
            if (world.getBlockState(at).isSolidBlock(world, at) && world.getBlockState(at.up()).isSolidBlock(world, at.up())) {
                return false;
            }
            if (!overhead(world, at)) {
                return true;
            }
        }
        return false;
    }

    private static boolean lowerDoor(net.minecraft.block.BlockState st) {
        return st.getBlock() instanceof DoorBlock && st.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER;
    }

    /** Every door on the ground floor of a place's lot. */
    private static List<Door> doorsIn(ServerWorld world, int ox, int oy, int oz, TownLayout.Site s) {
        List<Door> out = new ArrayList<>();
        for (int x = ox + s.x; x < ox + s.x + s.w; x++) {
            for (int z = oz + s.z; z < oz + s.z + s.d; z++) {
                for (int y = oy - 1; y <= oy + 2; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    var st = world.getBlockState(p);
                    if (!lowerDoor(st)) {
                        continue;
                    }
                    var f = st.get(DoorBlock.FACING);
                    var side = f.rotateYClockwise();
                    boolean single = !lowerDoor(world.getBlockState(p.offset(side))) && !lowerDoor(world.getBlockState(p.offset(side.getOpposite())));
                    boolean front = !skyOut(world, p, f), back = !skyOut(world, p, f.getOpposite());
                    out.add(new Door(p, single, front == back ? null : front ? f.getOpposite() : f));
                }
            }
        }
        return out;
    }

    /** The place a cell (layout x, z) belongs to, or null. */
    private static TownLayout.Site siteAt(TownLayout l, int x, int z) {
        for (TownLayout.Site s : l.sites) {
            if (x >= s.x && x < s.x + s.w && z >= s.z && z < s.z + s.d) {
                return s;
            }
        }
        return null;
    }

    private static boolean roofed(ServerWorld world, BlockPos feet) {
        for (int y = 2; y <= 12; y++) {
            BlockPos p = feet.up(y);
            if (world.getBlockState(p).isSolidBlock(world, p)) {
                return true;
            }
        }
        return false;
    }

    /** A free spot in a place's lot at about the given feet height, nearest its middle (roofed ones first for a building). */
    private static BlockPos spotIn(ServerWorld world, int ox, int oz, TownLayout.Site s, int feet, boolean indoor) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        double mx = ox + s.x + s.w / 2.0, mz = oz + s.z + s.d / 2.0;
        for (int x = ox + s.x + 1; x < ox + s.x + s.w - 1; x++) {
            for (int z = oz + s.z + 1; z < oz + s.z + s.d - 1; z++) {
                for (int y = feet - 1; y <= feet + 1; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!standable(world, p)) {
                        continue;
                    }
                    double dd = Math.hypot(x + 0.5 - mx, z + 0.5 - mz) + Math.abs(y - feet) * 0.5 + (indoor && !roofed(world, p) ? 1000 : 0);
                    if (dd < bestD) {
                        best = p;
                        bestD = dd;
                    }
                }
            }
        }
        return best;
    }

    private static com.google.gson.JsonArray xyz(int x, int y, int z) {
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        a.add(x);
        a.add(y);
        a.add(z);
        return a;
    }

    private static List<String> townCheck(ServerWorld world, String id, com.foredayner.scenario.town.BuildState state, TownLayout l,
                                          ScenarioSheet sheet) throws java.io.IOException {
        List<String> out = new ArrayList<>();
        int ox = state.originX, oy = state.originY, oz = state.originZ;
        java.util.function.BiConsumer<Boolean, String> ok = (pass, what) -> out.add((pass ? "ok   " : "FAIL ") + what);
        ok.accept("done".equals(state.status), "the build finished (" + state.status + ")");
        for (ScenarioSheet.Place p : sheet.places) {
            var bp = state.places.get(p.id);
            boolean built = bp != null && "done".equals(bp.state) && (bp.error == null || bp.error.isEmpty());
            ok.accept(built, "place " + p.id + " built" + (bp == null ? "" : " (" + bp.state + (bp.error == null || bp.error.isEmpty() ? "" : ": " + bp.error) + ")"));
        }
        // Stairs: up to every floor of a building with floors, down into every cellar.
        for (ScenarioSheet.Place p : sheet.places) {
            TownLayout.Site s = l.site(p.id);
            if (s == null || p.outdoor) {
                continue;
            }
            if (p.floors > 1) {
                int n = countStairs(world, ox + s.x, oy + 1, oz + s.z, ox + s.x + s.w - 1, oy + 5 * (p.floors - 1) + 1, oz + s.z + s.d - 1);
                ok.accept(n >= 4, p.id + ": stairs to the floor above (" + n + " stair blocks)");
            }
            if (p.cellars > 0) {
                int n = countStairs(world, ox + s.x, oy - 5 * p.cellars - 1, oz + s.z, ox + s.x + s.w - 1, oy, oz + s.z + s.d - 1);
                ok.accept(n >= 4, p.id + ": stairs down to the cellar (" + n + " stair blocks)");
            }
        }
        // Tunnels: secret ones shut by a false wall where they meet a cellar.
        for (var t : com.foredayner.scenario.layout.Underground.tunnels(l, sheet)) {
            int f = oy - 1 - com.foredayner.scenario.layout.Underground.LEVEL_H;
            int open = 0, shut = 0;
            for (long k : t.doorways()) {
                BlockPos at = new BlockPos(ox + com.foredayner.scenario.layout.Underground.keyX(k), f + 1, oz + com.foredayner.scenario.layout.Underground.keyZ(k));
                if (world.getBlockState(at).isSolidBlock(world, at)) {
                    shut++;
                } else {
                    open++;
                }
            }
            int inside = 0;
            for (long k : t.inside()) {
                BlockPos at = new BlockPos(ox + com.foredayner.scenario.layout.Underground.keyX(k), f + 1, oz + com.foredayner.scenario.layout.Underground.keyZ(k));
                if (world.getBlockState(at).isAir()) {
                    inside++;
                }
            }
            ok.accept(inside >= t.inside().size() * 0.8, "tunnel " + t.from() + " - " + t.to() + ": dug (" + inside + " of " + t.inside().size() + ")");
            ok.accept(t.secret() ? shut > 0 && open == 0 : open > 0, "tunnel " + t.from() + " - " + t.to() + (t.secret() ? ": shut by a false wall" : ": open")
                + " (" + shut + " shut, " + open + " open)");
        }
        // Every clue has its spot, not inside a solid block, in its place's lot (or its cellar).
        for (ScenarioSheet.Clue c : sheet.clues) {
            var bp = state.places.get(c.place);
            var spots = bp == null ? null : bp.marks.get(BuildPrompts.clueMark(c));
            boolean has = spots != null && !spots.isEmpty();
            String where = has ? String.format(Locale.ROOT, " at %d,%d,%d (%s)", spots.get(0)[0], spots.get(0)[1], spots.get(0)[2],
                world.getBlockState(new BlockPos(spots.get(0)[0], spots.get(0)[1], spots.get(0)[2])).getBlock()) : "";
            ok.accept(has, "clue " + c.id + " (" + c.kind() + ") has its spot" + where);
            if (has) {
                BlockPos p = new BlockPos(spots.get(0)[0], spots.get(0)[1], spots.get(0)[2]);
                ok.accept(!world.getBlockState(p).isSolidBlock(world, p), "  clue " + c.id + " is not inside a solid block");
                TownLayout.Site s = l.site(c.place);
                ok.accept(s != null && p.getX() >= ox + s.x && p.getX() < ox + s.x + s.w && p.getZ() >= oz + s.z && p.getZ() < oz + s.z + s.d,
                    "  clue " + c.id + " lies in its place's lot");
                if (BuildPrompts.underground(c)) {
                    ok.accept(p.getY() < oy - 1, "  clue " + c.id + " is down in the cellar (y " + p.getY() + ")");
                }
            }
        }
        // Doors: a step up to every outside door whose ground is a block lower (Evelyn's house had none
        // drawn), facing the door; and the house's inner door stays a single one.
        for (ScenarioSheet.Place p : sheet.places) {
            TownLayout.Site s = l.site(p.id);
            if (s == null || p.outdoor) {
                continue;
            }
            List<Door> doors = doorsIn(world, ox, oy, oz, s);
            if ("에블린의 집".equals(p.name)) {
                int steps = 0, outside = 0;
                for (Door d : doors) {
                    if (d.out() == null) {
                        continue;
                    }
                    outside++;
                    var st = world.getBlockState(d.at().offset(d.out()).down());
                    boolean good = st.getBlock() instanceof StairsBlock && st.get(StairsBlock.FACING) == d.out().getOpposite();
                    if (good) {
                        steps++;
                    }
                    out.add("       outside door " + d.at().toShortString() + " out " + d.out() + ": " + st);
                }
                ok.accept(outside > 0 && steps == outside, p.id + ": a step up to each outside door, facing it (" + steps + " of " + outside + ")");
                ok.accept(doors.stream().anyMatch(d -> d.out() == null && d.single()), p.id + ": its inner door is one block wide");
            }
        }
        // Every flight faces the way it rises (the town hall's were drawn back to front).
        var fl = state.flights == null ? List.<com.foredayner.scenario.town.Flights.Flight>of() : state.flights;
        for (var flight : fl) {
            int wrong = 0;
            for (int[] st : flight.steps) {
                var b0 = world.getBlockState(new BlockPos(st[0], st[1], st[2]));
                if (!(b0.getBlock() instanceof StairsBlock) || !b0.get(StairsBlock.FACING).asString().equals(flight.dir)) {
                    wrong++;
                }
            }
            ok.accept(wrong == 0, "flight " + flight.from + " -> " + flight.to + " " + flight.dir + ": every step faces up it (" + wrong + " wrong)");
        }
        // A floating paper over every clue's spot, for everyone to see.
        net.minecraft.util.math.Box area = new net.minecraft.util.math.Box(ox - 8, oy - 48, oz - 8, ox + l.size + 8, oy + 64, oz + l.size + 8);
        int papers = world.getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity.class, area,
            e -> e.getCommandTags().contains(BuildService.SPOT_TAG)).size();
        ok.accept(papers == 0, "no paper floats over the clue spots (they are not shown in the world): " + papers);
        // "여기로": a clue's spot moved to where the keeper stands; the clues file says so; and back.
        {
            ScenarioSheet.Clue c0 = sheet.clues.get(0);
            Path file = BuildService.cluesFile(id);
            int[] was = null;
            var before = com.google.gson.JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var el : before.getAsJsonArray("clues")) {
                var o = el.getAsJsonObject();
                if (c0.id.equals(o.get("id").getAsString())) {
                    was = new int[] { o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt() };
                }
            }
            if (was != null) {
                BlockPos to = new BlockPos(was[0] + 1, was[1], was[2]);
                out.add("       " + BuildService.moveClueTo(id, c0.id, to));
                boolean moved = false;
                var after = com.google.gson.JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var el : after.getAsJsonArray("clues")) {
                    var o = el.getAsJsonObject();
                    moved |= c0.id.equals(o.get("id").getAsString()) && o.get("x").getAsInt() == to.getX() && o.get("z").getAsInt() == to.getZ();
                }
                var inWorld = world.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("blockworld").resolve("clues.json");
                boolean worldToo = Files.readString(inWorld, StandardCharsets.UTF_8).contains("\"x\": " + to.getX());
                ok.accept(moved && worldToo, "\"여기로\" moves " + c0.id + " in the clues file (the world's copy too)");
                BuildService.moveClueTo(id, c0.id, new BlockPos(was[0], was[1], was[2]));
            } else {
                ok.accept(false, c0.id + " is not in the clues file");
            }
        }
        // The clues file is in the world's own folder too.
        Path inWorld = world.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("blockworld").resolve("clues.json");
        ok.accept(Files.exists(inWorld), "the clues file is in the world's folder (blockworld/clues.json)");
        var f = BuildService.cluesFile(id);
        if (Files.exists(f)) {
            var json = com.google.gson.JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            int n = json.getAsJsonArray("clues").size();
            int buried = 0;
            for (var el : json.getAsJsonArray("clues")) {
                var o = el.getAsJsonObject();
                BlockPos at = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
                if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty() || !world.getFluidState(at).isEmpty()) {
                    buried++;
                    out.add("       clue " + o.get("id").getAsString() + " inside " + world.getBlockState(at).getBlock());
                }
            }
            ok.accept(buried == 0, "no clue spot is inside a block (" + buried + ")");
            int named = json.has("places") ? json.getAsJsonArray("places").size() : 0;
            ok.accept(named >= sheet.places.size(), "clues file names " + named + " places (" + sheet.places.size() + " in the sheet)");
            ok.accept(json.has("town") && json.getAsJsonObject("town").has("x2"), "clues file has the town's lay-out bounds");
            ok.accept(n == sheet.clues.size(), "clues file has " + n + " of " + sheet.clues.size() + " clues");
            int want = 0;
            for (ScenarioSheet.Place p : sheet.places) {
                if (!p.outdoor) {
                    want += Math.max(0, p.floors - 1) + Math.max(0, p.cellars);
                }
            }
            int got = json.has("stairs") ? json.getAsJsonArray("stairs").size() : 0;
            ok.accept(got == want, "clues file has " + got + " flights of stairs (" + want + " wanted: up to each floor, down to each cellar)");
            if (json.has("stairs")) {
                for (var el : json.getAsJsonArray("stairs")) {
                    var o = el.getAsJsonObject();
                    out.add("       flight " + o.get("from") + " -> " + o.get("to") + " " + o.get("dir") + " from " + o.get("bottom") + " to " + o.get("top"));
                }
            }
        } else {
            ok.accept(false, "clues file written");
        }
        return out;
    }

    private static int countStairs(ServerWorld world, int x1, int y1, int z1, int x2, int y2, int z2) {
        int n = 0;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    if (world.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof StairsBlock) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** The town for the play test: every place (and each upper floor and cellar) with a spot to walk to, a start on a street. */
    private static String townPlaytest(ServerWorld world, com.foredayner.scenario.town.BuildState state, TownLayout l, ScenarioSheet sheet) {
        int ox = state.originX, oy = state.originY, oz = state.originZ;
        com.google.gson.JsonObject out = new com.google.gson.JsonObject();
        out.add("origin", xyz(ox, oy, oz));
        int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        for (TownLayout.Site s : l.sites) {
            x1 = Math.min(x1, s.x);
            z1 = Math.min(z1, s.z);
            x2 = Math.max(x2, s.x + s.w - 1);
            z2 = Math.max(z2, s.z + s.d - 1);
        }
        com.google.gson.JsonArray box = new com.google.gson.JsonArray();
        for (int v : new int[] { ox + x1 - 4, oz + z1 - 4, ox + x2 + 4, oz + z2 + 4 }) {
            box.add(v);
        }
        out.add("bounds", box);
        // Start on a street near the middle of the places.
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        BlockPos start = null;
        double bestD = Double.MAX_VALUE;
        for (TownLayout.Road r : l.roads) {
            for (int[] pt : r.points) {
                double dd = Math.hypot(pt[0] - mx, pt[1] - mz);
                if (dd < bestD && siteAt(l, pt[0], pt[1]) == null) {
                    int top = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ox + pt[0], oz + pt[1]);
                    BlockPos feet = new BlockPos(ox + pt[0], top, oz + pt[1]);
                    if (standable(world, feet)) {
                        start = feet;
                        bestD = dd;
                    }
                }
            }
        }
        if (start == null) {
            start = new BlockPos(ox + mx, world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ox + mx, oz + mz), oz + mz);
        }
        out.add("entrance", xyz(start.getX(), start.getY(), start.getZ()));
        // Secret doors: the false walls where a secret tunnel meets a cellar (feet height, both blocks above).
        com.google.gson.JsonArray secrets = new com.google.gson.JsonArray();
        int tf = oy - com.foredayner.scenario.layout.Underground.LEVEL_H; // feet in the first cellar floor
        for (var t : com.foredayner.scenario.layout.Underground.tunnels(l, sheet)) {
            if (t.secret()) {
                for (long k : t.doorways()) {
                    secrets.add(xyz(ox + com.foredayner.scenario.layout.Underground.keyX(k), tf, oz + com.foredayner.scenario.layout.Underground.keyZ(k)));
                }
            }
        }
        out.add("secretDoors", secrets);
        // A one-block-wide inner door (Evelyn's house): where to stand before it, the door, and the
        // block past it where a click on the door should take one.
        for (ScenarioSheet.Place p : sheet.places) {
            TownLayout.Site s = l.site(p.id);
            if (s == null || p.outdoor) {
                continue;
            }
            for (Door d : doorsIn(world, ox, oy, oz, s)) {
                if (d.out() != null || !d.single() || out.has("singleDoor")) {
                    continue;
                }
                var f = world.getBlockState(d.at()).get(DoorBlock.FACING);
                BlockPos before = d.at().offset(f, 2), past = d.at().offset(f.getOpposite());
                if (!standable(world, before) || !standable(world, past)) {
                    before = d.at().offset(f.getOpposite(), 2);
                    past = d.at().offset(f);
                }
                if (standable(world, before) && standable(world, past)) {
                    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                    o.addProperty("place", p.id);
                    o.add("door", xyz(d.at().getX(), d.at().getY(), d.at().getZ()));
                    o.add("before", xyz(before.getX(), before.getY(), before.getZ()));
                    o.add("past", xyz(past.getX(), past.getY(), past.getZ()));
                    out.add("singleDoor", o);
                }
            }
        }
        com.google.gson.JsonArray rooms = new com.google.gson.JsonArray();
        for (ScenarioSheet.Place p : sheet.places) {
            TownLayout.Site s = l.site(p.id);
            if (s == null) {
                continue;
            }
            List<Object[]> parts = new ArrayList<>();
            parts.add(new Object[] { p.id, 0, p.outdoor ? oy : oy + 1, !p.outdoor });
            for (int k = 1; k < p.floors && !p.outdoor; k++) {
                parts.add(new Object[] { p.id + "#" + (k + 1) + "f", k, oy + 1 + 5 * k, true });
            }
            for (int k = 1; k <= p.cellars; k++) {
                parts.add(new Object[] { p.id + "#cellar" + (k > 1 ? k : ""), -k, oy - 5 * k, false });
            }
            for (Object[] part : parts) {
                com.google.gson.JsonObject r = new com.google.gson.JsonObject();
                r.addProperty("id", (String) part[0]);
                r.addProperty("level", (Integer) part[1]);
                r.addProperty("floorY", (Integer) part[2]);
                BlockPos at = spotIn(world, ox, oz, s, (Integer) part[2], (Boolean) part[3]);
                if (at != null) {
                    r.add("stand", xyz(at.getX(), at.getY(), at.getZ()));
                }
                rooms.add(r);
            }
        }
        out.add("rooms", rooms);
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out);
    }

    private static void townPhotos(ClientGameTestContext context, TestSingleplayerContext game, TownLayout l, int[] o, ScenarioSheet sheet) {
        game.getServer().runCommand("gamemode spectator @a");
        context.runOnClient(client -> client.options.hudHidden = true);
        int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        for (TownLayout.Site s : l.sites) {
            x1 = Math.min(x1, s.x);
            z1 = Math.min(z1, s.z);
            x2 = Math.max(x2, s.x + s.w - 1);
            z2 = Math.max(z2, s.z + s.d - 1);
        }
        double cx = o[0] + (x1 + x2) / 2.0, cz = o[2] + (z1 + z2) / 2.0;
        look(context, game, cx, o[1] + 70, cz + 50, cx, o[1], cz);
        context.takeScreenshot("hand_town_above");
        look(context, game, cx - 60, o[1] + 30, cz + 60, cx, o[1], cz);
        context.takeScreenshot("hand_town_sw");
        for (TownLayout.Site s : l.sites) {
            double sx = o[0] + s.x + s.w / 2.0, sz = o[2] + s.z + s.d / 2.0;
            double fx = 0, fz = 1;
            switch (s.front == null ? "south" : s.front) {
                case "north" -> { fx = 0; fz = -1; }
                case "east" -> { fx = 1; fz = 0; }
                case "west" -> { fx = -1; fz = 0; }
                default -> { }
            }
            double r = Math.max(s.w, s.d) * 0.9 + 6;
            look(context, game, sx + fx * r - fz * r * 0.4, o[1] + 10, sz + fz * r + fx * r * 0.4, sx, o[1] + 2, sz);
            context.takeScreenshot("hand_place_" + s.id);
        }
        context.runOnClient(client -> client.options.hudHidden = false);
    }

    // ------------------------------------------------------------------ checks

    private static int floorY(int originY, int level) {
        return originY - 1 + level * IndoorPlan.FLOOR_H;
    }

    private static int[] step(String dir) {
        return switch (dir == null ? "" : dir) {
            case "east" -> new int[] { 1, 0 };
            case "south" -> new int[] { 0, 1 };
            case "west" -> new int[] { -1, 0 };
            default -> new int[] { 0, -1 };
        };
    }

    private static List<String> check(ServerWorld world, String id, com.foredayner.scenario.town.BuildState state) throws java.io.IOException {
        List<String> out = new ArrayList<>();
        TownLayout l = LayoutService.load(id);
        ScenarioSheet sheet = SheetService.load(id);
        if (!l.building()) {
            return townCheck(world, id, state, l, sheet);
        }
        int ox = state.originX, oy = state.originY, oz = state.originZ;
        java.util.function.BiConsumer<Boolean, String> ok = (pass, what) -> out.add((pass ? "ok   " : "FAIL ") + what);
        ok.accept("done".equals(state.status), "the build finished (" + state.status + ")");
        for (ScenarioSheet.Place p : sheet.places) {
            var bp = state.places.get(p.id);
            ok.accept(bp != null && "done".equals(bp.state), "room " + p.id + " built" + (bp == null ? "" : " (" + bp.state + " " + bp.error + ")"));
        }
        // Every flight: four steps, and the hole over the upper three.
        for (TownLayout.Stair st : l.stairs) {
            int fy = floorY(oy, st.level);
            int[] s = step(st.dir);
            boolean steps = true, hole = true;
            for (int i = 0; i < IndoorPlan.STAIR_RUN; i++) {
                BlockPos at = new BlockPos(ox + st.x + s[0] * i, fy + 1 + i, oz + st.z + s[1] * i);
                steps &= world.getBlockState(at).getBlock() instanceof StairsBlock;
                if (i >= 1) {
                    for (int y = fy + IndoorPlan.FLOOR_H; y <= fy + IndoorPlan.FLOOR_H + 1; y++) {
                        BlockPos over = new BlockPos(at.getX(), y, at.getZ());
                        hole &= !world.getBlockState(over).isSolidBlock(world, over);
                    }
                }
            }
            BlockPos last = new BlockPos(ox + st.x + s[0] * IndoorPlan.STAIR_RUN, fy + IndoorPlan.FLOOR_H, oz + st.z + s[1] * IndoorPlan.STAIR_RUN);
            steps &= world.getBlockState(last).getBlock() instanceof StairsBlock;
            ok.accept(steps, String.format(Locale.ROOT, "stairs level %d at (%d,%d) %s: all five steps (the last in the floor above)", st.level, st.x, st.z, st.dir));
            ok.accept(hole, String.format(Locale.ROOT, "stairs level %d: the way up is open", st.level));
            // The landing: two blocks of air to step off into.
            BlockPos land = new BlockPos(ox + st.x + s[0] * IndoorPlan.STAIR_RUN, fy + IndoorPlan.FLOOR_H + 1, oz + st.z + s[1] * IndoorPlan.STAIR_RUN);
            // (An invisible light block there is nothing in the way.)
            ok.accept(!world.getBlockState(land).isSolidBlock(world, land) && world.getBlockState(land.up()).getCollisionShape(world, land.up()).isEmpty(),
                String.format(Locale.ROOT, "stairs level %d: the landing is clear (%s / %s)", st.level, world.getBlockState(land).getBlock(),
                    world.getBlockState(land.up()).getBlock()));
        }
        // Doors: real doors, the secret one a false wall; nothing in front of either side.
        for (TownLayout.Door d : l.doors) {
            int fy = floorY(oy, d.level);
            BlockPos at = new BlockPos(ox + d.x, fy + 1, oz + d.z);
            var block = world.getBlockState(at).getBlock();
            if (d.secret) {
                ok.accept(!(block instanceof DoorBlock) && world.getBlockState(at).isSolidBlock(world, at)
                        && world.getBlockState(at.up()).isSolidBlock(world, at.up()),
                    String.format(Locale.ROOT, "secret door level %d at (%d,%d): a false wall of %s", d.level, d.x, d.z, block));
            } else {
                ok.accept(block instanceof DoorBlock, String.format(Locale.ROOT, "door level %d at (%d,%d)%s: %s", d.level, d.x, d.z,
                    d.entrance ? " (entrance)" : "", block));
            }
            for (int[] o : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                int x = d.x + o[0], z = d.z + o[1];
                boolean inside = false;
                for (TownLayout.Site s : l.sites) {
                    if (s.level == d.level && x > s.x && x < s.x + s.w - 1 && z > s.z && z < s.z + s.d - 1) {
                        inside = true;
                    }
                }
                if (!inside) {
                    continue;
                }
                BlockPos front = new BlockPos(ox + x, fy + 1, oz + z);
                boolean clear = !world.getBlockState(front).isSolidBlock(world, front) && !world.getBlockState(front.up()).isSolidBlock(world, front.up());
                ok.accept(clear, String.format(Locale.ROOT, "  in front of it at (%d,%d): clear (%s / %s)", x, z,
                    world.getBlockState(front).getBlock(), world.getBlockState(front.up()).getBlock()));
            }
        }
        // Every clue has its spot, in the air of its room.
        for (ScenarioSheet.Clue c : sheet.clues) {
            var bp = state.places.get(c.place);
            var spots = bp == null ? null : bp.marks.get(BuildPrompts.clueMark(c));
            boolean has = spots != null && !spots.isEmpty();
            String where = has ? String.format(Locale.ROOT, " at %d,%d,%d (%s)", spots.get(0)[0], spots.get(0)[1], spots.get(0)[2],
                world.getBlockState(new BlockPos(spots.get(0)[0], spots.get(0)[1], spots.get(0)[2])).getBlock()) : "";
            ok.accept(has, "clue " + c.id + " (" + c.kind() + ") has its spot" + where);
            if (has) {
                BlockPos p = new BlockPos(spots.get(0)[0], spots.get(0)[1], spots.get(0)[2]);
                ok.accept(!world.getBlockState(p).isSolidBlock(world, p), "  clue " + c.id + " is not inside a solid block");
                TownLayout.Site s = l.site(c.place);
                int fy = floorY(oy, s.level);
                ok.accept(p.getX() > ox + s.x && p.getX() < ox + s.x + s.w - 1 && p.getZ() > oz + s.z && p.getZ() < oz + s.z + s.d - 1
                    && p.getY() > fy && p.getY() < fy + IndoorPlan.FLOOR_H, "  clue " + c.id + " lies inside its room");
            }
        }
        // The clues file Foundry reads: every clue, with its kind and checks.
        var f = BuildService.cluesFile(id);
        if (Files.exists(f)) {
            var json = com.google.gson.JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            int n = json.getAsJsonArray("clues").size();
            int buried = 0;
            for (var el : json.getAsJsonArray("clues")) {
                var o = el.getAsJsonObject();
                BlockPos at = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
                if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty() || !world.getFluidState(at).isEmpty()) {
                    buried++;
                    out.add("       clue " + o.get("id").getAsString() + " inside " + world.getBlockState(at).getBlock());
                }
            }
            ok.accept(buried == 0, "no clue spot is inside a block (" + buried + ")");
            int named = json.has("places") ? json.getAsJsonArray("places").size() : 0;
            ok.accept(named >= sheet.places.size(), "clues file names " + named + " places (" + sheet.places.size() + " in the sheet)");
            ok.accept(json.has("town") && json.getAsJsonObject("town").has("x2"), "clues file has the town's lay-out bounds");
            ok.accept(n == sheet.clues.size(), "clues file has " + n + " of " + sheet.clues.size() + " clues");
            int m = json.has("stairs") ? json.getAsJsonArray("stairs").size() : 0;
            ok.accept(m == l.stairs.size(), "clues file has " + m + " of " + l.stairs.size() + " flights of stairs");
        } else {
            ok.accept(false, "clues file written");
        }
        return out;
    }

    // ------------------------------------------------------------------ for the play test

    /** A cell a character can stand in: two blocks of air (or rugs) on something solid. */
    private static boolean standable(ServerWorld world, BlockPos feet) {
        var below = feet.down();
        return world.getBlockState(below).isSolidBlock(world, below)
            // nothing at the feet higher than a rug
            && world.getBlockState(feet).getCollisionShape(world, feet).getMax(net.minecraft.util.math.Direction.Axis.Y) <= 0.1
            // not in water, nor among crops (a character walks round those)
            && world.getFluidState(feet).isEmpty() && !(world.getBlockState(feet).getBlock() instanceof net.minecraft.block.CropBlock)
            && world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty() && world.getFluidState(feet.up()).isEmpty();
    }

    /**
     * Where a play test on a real server walks to (world blocks, feet): just outside the entrance,
     * and a free spot in each room nearest its middle; with the stage's floors. The relay's play
     * test reads it with the clues file.
     */
    private static String playtest(ServerWorld world, String id, com.foredayner.scenario.town.BuildState state) throws java.io.IOException {
        TownLayout l = LayoutService.load(id);
        if (!l.building()) {
            return townPlaytest(world, state, l, SheetService.load(id));
        }
        int ox = state.originX, oy = state.originY, oz = state.originZ;
        com.google.gson.JsonObject out = new com.google.gson.JsonObject();
        com.google.gson.JsonArray origin = new com.google.gson.JsonArray();
        origin.add(ox);
        origin.add(oy);
        origin.add(oz);
        out.add("origin", origin);
        int[] b = IndoorPlan.bounds(l);
        com.google.gson.JsonArray box = new com.google.gson.JsonArray();
        for (int v : new int[] { ox + b[0], oz + b[1], ox + b[2], oz + b[3] }) {
            box.add(v);
        }
        out.add("bounds", box);
        for (TownLayout.Door d : l.doors) {
            if (!d.entrance) {
                continue;
            }
            // The outside cell next to the door (the one not in any room), a step further out.
            for (int[] o : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
                int x = d.x + o[0], z = d.z + o[1];
                boolean inRoom = l.sites.stream().anyMatch(s -> s.level == d.level && x > s.x && x < s.x + s.w - 1 && z > s.z && z < s.z + s.d - 1);
                boolean wall = l.sites.stream().anyMatch(s -> s.level == d.level && x >= s.x && x <= s.x + s.w - 1 && z >= s.z && z <= s.z + s.d - 1);
                if (!inRoom && !wall) {
                    com.google.gson.JsonArray e = new com.google.gson.JsonArray();
                    e.add(ox + d.x + o[0] * 3);
                    e.add(floorY(oy, d.level) + 1);
                    e.add(oz + d.z + o[1] * 3);
                    out.add("entrance", e);
                }
            }
        }
        // Secret doors (false walls): the two blocks one walks through, feet and head.
        com.google.gson.JsonArray secrets = new com.google.gson.JsonArray();
        for (TownLayout.Door d : l.doors) {
            if (d.secret) {
                com.google.gson.JsonArray at = new com.google.gson.JsonArray();
                at.add(ox + d.x);
                at.add(floorY(oy, d.level) + 1);
                at.add(oz + d.z);
                secrets.add(at);
            }
        }
        out.add("secretDoors", secrets);
        com.google.gson.JsonArray rooms = new com.google.gson.JsonArray();
        for (TownLayout.Site s : l.sites) {
            int fy = floorY(oy, s.level);
            double mx = ox + s.x + s.w / 2.0, mz = oz + s.z + s.d / 2.0;
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int x = ox + s.x + 1; x < ox + s.x + s.w - 1; x++) {
                for (int z = oz + s.z + 1; z < oz + s.z + s.d - 1; z++) {
                    BlockPos feet = new BlockPos(x, fy + 1, z);
                    double dd = Math.hypot(x + 0.5 - mx, z + 0.5 - mz);
                    if (dd < bestD && standable(world, feet)) {
                        best = feet;
                        bestD = dd;
                    }
                }
            }
            com.google.gson.JsonObject r = new com.google.gson.JsonObject();
            r.addProperty("id", s.id);
            r.addProperty("level", s.level);
            r.addProperty("floorY", fy + 1);
            if (best != null) {
                com.google.gson.JsonArray at = new com.google.gson.JsonArray();
                at.add(best.getX());
                at.add(best.getY());
                at.add(best.getZ());
                r.add("stand", at);
            }
            rooms.add(r);
        }
        out.add("rooms", rooms);
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out);
    }

    // ------------------------------------------------------------------ pictures

    private static void photos(ClientGameTestContext context, TestSingleplayerContext game, TownLayout l, int[] o) {
        game.getServer().runCommand("gamemode spectator @a");
        context.runOnClient(client -> client.options.hudHidden = true);
        int[] b = IndoorPlan.bounds(l);
        double cx = o[0] + (b[0] + b[2]) / 2.0, cz = o[2] + (b[1] + b[3]) / 2.0;
        int top = 4 * IndoorPlan.FLOOR_H;
        // Outside: from the south-west (the entrance side) and the south-east.
        look(context, game, o[0] + b[0] - 14, o[1] + 8, o[2] + b[3] + 18, cx, o[1] + top * 0.4, cz);
        context.takeScreenshot("hand_outside_sw");
        look(context, game, o[0] + b[2] + 16, o[1] + 14, o[2] + b[3] + 16, cx, o[1] + top * 0.4, cz);
        context.takeScreenshot("hand_outside_se");
        look(context, game, cx, o[1] + 45, cz + 30, cx, o[1], cz);
        context.takeScreenshot("hand_above");
        // Each furnished room, from up under its ceiling, looking down across it (both ways).
        for (TownLayout.Site s : l.sites) {
            if (s.id.startsWith("_")) {
                continue;
            }
            int fy = floorY(o[1], s.level);
            double x1 = o[0] + s.x + 1.5, z1 = o[2] + s.z + 1.5, x2 = o[0] + s.x + s.w - 1.5, z2 = o[2] + s.z + s.d - 1.5;
            double eye = fy + 3.2; // feet; the eyes are just under the ceiling
            look(context, game, x1, eye, z1, x2, fy + 1, z2);
            context.takeScreenshot("hand_room_" + s.id + "_a");
            look(context, game, x2, eye, z2, x1, fy + 1, z1);
            context.takeScreenshot("hand_room_" + s.id + "_b");
        }
        // Each flight from its foot, looking up it.
        for (TownLayout.Stair st : l.stairs) {
            int fy = floorY(o[1], st.level);
            int[] s = step(st.dir);
            double fx = o[0] + st.x + 0.5 - s[0] * 2, fz = o[2] + st.z + 0.5 - s[1] * 2;
            look(context, game, fx, fy + 1, fz, fx + s[0] * 6, fy + 1 + IndoorPlan.FLOOR_H, fz + s[1] * 6);
            context.takeScreenshot("hand_stairs_" + (st.level < 0 ? "b" + (-st.level) : String.valueOf(st.level)));
        }
        context.runOnClient(client -> client.options.hudHidden = false);
    }

    private static void clueShots(ClientGameTestContext context, TestSingleplayerContext game) {
        List<int[]> spots = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        try {
            String json = Files.readString(OUT.resolve("clues.json"), StandardCharsets.UTF_8);
            for (var el : com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("clues")) {
                var c = el.getAsJsonObject();
                spots.add(new int[] { c.get("x").getAsInt(), c.get("y").getAsInt(), c.get("z").getAsInt() });
                ids.add(c.get("id").getAsString());
            }
        } catch (Exception e) {
            return;
        }
        game.getServer().runCommand("gamemode spectator @a");
        context.runOnClient(client -> client.options.hudHidden = true);
        for (int k = 0; k < spots.size(); k++) {
            int[] c = spots.get(k);
            // From the room's middle side: a step back and up, looking at the spot.
            look(context, game, c[0] + 0.5 + 1.6, c[1] + 0.6, c[2] + 0.5 + 1.6, c[0] + 0.5, c[1] + 0.3, c[2] + 0.5);
            context.takeScreenshot("hand_clue_" + ids.get(k));
        }
        context.runOnClient(client -> client.options.hudHidden = false);
    }

    /** Stand at one point (feet) and look at another. */
    private static void look(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z,
                             double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - (y + 1.62), dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        game.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f", x, y, z));
        context.waitTicks(10);
        context.runOnClient(client -> {
            client.player.setYaw(yaw);
            client.player.setPitch(pitch);
        });
        context.waitTicks(25);
        try {
            game.getClientWorld().waitForChunksRender();
        } catch (AssertionError slow) {
            context.waitTicks(60);
        }
    }
}
