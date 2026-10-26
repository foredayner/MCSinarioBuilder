package com.foredayner.scenario.net;

import com.foredayner.scenario.ScenarioBuilderMod;
import com.foredayner.scenario.input.ScenarioText;
import com.foredayner.scenario.store.ScenarioStore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Messages between the workshop screen and the server. */
public final class ScenarioNet {

    public static final int OP_LEVEL = 2;
    /** Bytes per upload part (client packets are limited). */
    public static final int PART_SIZE = 30_000;
    private static final int MAX_PARTS = 40;

    private ScenarioNet() {
    }

    /** Client -> server: one part of a gzipped scenario text. */
    public record UploadC2S(int id, int index, int total, String name, boolean cut, byte[] data) implements CustomPayload {
        public static final Id<UploadC2S> ID = new Id<>(Identifier.of(ScenarioBuilderMod.MOD_ID, "upload"));
        public static final PacketCodec<RegistryByteBuf, UploadC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, UploadC2S::id,
            PacketCodecs.VAR_INT, UploadC2S::index,
            PacketCodecs.VAR_INT, UploadC2S::total,
            PacketCodecs.STRING, UploadC2S::name,
            PacketCodecs.BOOLEAN, UploadC2S::cut,
            PacketCodecs.BYTE_ARRAY, UploadC2S::data,
            UploadC2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: "list", "delete" (arg = scenario id). */
    public record RequestC2S(String action, String arg) implements CustomPayload {
        public static final Id<RequestC2S> ID = new Id<>(Identifier.of(ScenarioBuilderMod.MOD_ID, "request"));
        public static final PacketCodec<RegistryByteBuf, RequestC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, RequestC2S::action, PacketCodecs.STRING, RequestC2S::arg, RequestC2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Server -> client: the stored scenarios (JSON) and a message for the workshop ("" = none). */
    public record StatusS2C(String scenarios, String message, String selected) implements CustomPayload {
        public static final Id<StatusS2C> ID = new Id<>(Identifier.of(ScenarioBuilderMod.MOD_ID, "status"));
        public static final PacketCodec<RegistryByteBuf, StatusS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, StatusS2C::scenarios, PacketCodecs.STRING, StatusS2C::message,
            PacketCodecs.STRING, StatusS2C::selected, StatusS2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Both ways: a scenario's sheet (gzipped JSON). Server -> client to show it; client -> server to save the GM's edits. */
    public record SheetPayload(String id, byte[] data) implements CustomPayload {
        public static final Id<SheetPayload> ID = new Id<>(Identifier.of(ScenarioBuilderMod.MOD_ID, "sheet"));
        public static final PacketCodec<RegistryByteBuf, SheetPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SheetPayload::id, PacketCodecs.BYTE_ARRAY, SheetPayload::data, SheetPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Both ways: a scenario's town layout (gzipped JSON). Server -> client to show it; client -> server to save the GM's edits. */
    public record LayoutPayload(String id, byte[] data) implements CustomPayload {
        public static final Id<LayoutPayload> ID = new Id<>(Identifier.of(ScenarioBuilderMod.MOD_ID, "layout"));
        public static final PacketCodec<RegistryByteBuf, LayoutPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, LayoutPayload::id, PacketCodecs.BYTE_ARRAY, LayoutPayload::data, LayoutPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Server -> client: how building a scenario's town goes (gzipped BuildState JSON), and whether it can be undone. */
    public record BuildPayload(String id, byte[] data, boolean undo) implements CustomPayload {
        public static final Id<BuildPayload> ID = new Id<>(Identifier.of(ScenarioBuilderMod.MOD_ID, "build"));
        public static final PacketCodec<RegistryByteBuf, BuildPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, BuildPayload::id, PacketCodecs.BYTE_ARRAY, BuildPayload::data, PacketCodecs.BOOLEAN, BuildPayload::undo,
            BuildPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client -> server: one part of a place's reference picture (a png, jpeg, gif or webp file). */
    public record RefUploadC2S(String id, String place, int index, int total, byte[] data) implements CustomPayload {
        public static final Id<RefUploadC2S> ID = new Id<>(Identifier.of(ScenarioBuilderMod.MOD_ID, "ref_upload"));
        public static final PacketCodec<RegistryByteBuf, RefUploadC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, RefUploadC2S::id, PacketCodecs.STRING, RefUploadC2S::place, PacketCodecs.VAR_INT, RefUploadC2S::index,
            PacketCodecs.VAR_INT, RefUploadC2S::total, PacketCodecs.BYTE_ARRAY, RefUploadC2S::data, RefUploadC2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Pictures are at most this many parts (4.5 MB). */
    private static final int MAX_REF_PARTS = 160;
    private static final Map<UUID, byte[][]> REF_UPLOADS = new HashMap<>();

    private static void receiveRef(ServerPlayerEntity player, RefUploadC2S p) {
        if (!allowed(player)) {
            return;
        }
        if (p.total() <= 0 || p.total() > MAX_REF_PARTS || p.index() < 0 || p.index() >= p.total() || p.data().length > PART_SIZE) {
            return;
        }
        byte[][] parts = REF_UPLOADS.get(player.getUuid());
        if (parts == null || parts.length != p.total() || p.index() == 0) {
            parts = new byte[p.total()][];
            REF_UPLOADS.put(player.getUuid(), parts);
        }
        parts[p.index()] = p.data();
        for (byte[] part : parts) {
            if (part == null) {
                return;
            }
        }
        REF_UPLOADS.remove(player.getUuid());
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            all.writeBytes(part);
        }
        try {
            String ref = com.foredayner.scenario.town.RefService.saveFile(p.id(), p.place(), all.toByteArray());
            com.foredayner.scenario.town.RefService.set(player.getServer(), player, p.id(), p.place(), ref, ok -> { });
        } catch (IOException | RuntimeException e) {
            status(player, "사진을 받지 못했어요: " + e.getMessage(), p.id());
        }
    }

    /** Server -> client: the model settings (JSON of ScenarioConfig). */
    public record ConfigS2C(String json) implements CustomPayload {
        public static final Id<ConfigS2C> ID = new Id<>(Identifier.of(ScenarioBuilderMod.MOD_ID, "config"));
        public static final PacketCodec<RegistryByteBuf, ConfigS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ConfigS2C::json, ConfigS2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static final class Assembly {
        final int id;
        final byte[][] parts;
        int received;

        Assembly(int id, int total) {
            this.id = id;
            this.parts = new byte[total][];
        }
    }

    private static final Map<UUID, Assembly> UPLOADS = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.playC2S().register(UploadC2S.ID, UploadC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestC2S.ID, RequestC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(StatusS2C.ID, StatusS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(SheetPayload.ID, SheetPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SheetPayload.ID, SheetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigS2C.ID, ConfigS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(LayoutPayload.ID, LayoutPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BuildPayload.ID, BuildPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RefUploadC2S.ID, RefUploadC2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RefUploadC2S.ID, (p, ctx) -> ctx.server().execute(() -> receiveRef(ctx.player(), p)));
        PayloadTypeRegistry.playC2S().register(LayoutPayload.ID, LayoutPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LayoutPayload.ID, (p, ctx) -> ctx.server().execute(() -> saveLayout(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(SheetPayload.ID, (p, ctx) -> ctx.server().execute(() -> saveSheet(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(UploadC2S.ID, (p, ctx) -> ctx.server().execute(() -> receive(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(RequestC2S.ID, (p, ctx) -> ctx.server().execute(() -> request(ctx.player(), p)));
    }

    static boolean allowed(ServerPlayerEntity player) {
        if (player.hasPermissionLevel(OP_LEVEL)) {
            return true;
        }
        player.sendMessage(Text.literal("관리자(OP)만 사용할 수 있어요."), true);
        return false;
    }

    public static void status(ServerPlayerEntity player, String message, String selected) {
        ServerPlayNetworking.send(player, new StatusS2C(ScenarioStore.listJson(), message == null ? "" : message,
            selected == null ? "" : selected));
    }

    private static void request(ServerPlayerEntity player, RequestC2S p) {
        if (!allowed(player)) {
            return;
        }
        switch (p.action()) {
            case "delete" -> {
                try {
                    ScenarioStore.delete(p.arg());
                    status(player, "시나리오를 지웠어요.", "");
                } catch (IOException e) {
                    status(player, "지우지 못했어요: " + e.getMessage(), "");
                }
            }
            case "analyze" -> com.foredayner.scenario.sheet.SheetService.analyze(player, p.arg());
            case "revise" -> {
                int nl = p.arg().indexOf('\n');
                if (nl > 0) {
                    com.foredayner.scenario.sheet.SheetService.revise(player, p.arg().substring(0, nl), p.arg().substring(nl + 1));
                }
            }
            case "ref_url", "ref_clear", "ref_again" -> {
                String[] a = p.arg().split("\n", 3);
                if (a.length >= 2) {
                    switch (p.action()) {
                        case "ref_url" -> com.foredayner.scenario.town.RefService.set(player.getServer(), player, a[0], a[1],
                            a.length > 2 ? a[2] : "", ok -> { });
                        case "ref_clear" -> com.foredayner.scenario.town.RefService.set(player.getServer(), player, a[0], a[1], "", ok -> { });
                        default -> com.foredayner.scenario.town.RefService.describe(player.getServer(), player, a[0], a[1], ok -> { });
                    }
                }
            }
            case "style" -> com.foredayner.scenario.town.StyleService.make(player.getServer(), player, p.arg(), guide -> { });
            case "build_here" -> com.foredayner.scenario.town.BuildService.here(player, p.arg());
            case "build_start" -> com.foredayner.scenario.town.BuildService.start(player, p.arg());
            case "build_stop" -> com.foredayner.scenario.town.BuildService.stop(player, p.arg());
            case "build_undo" -> com.foredayner.scenario.town.BuildService.undo(player, p.arg());
            case "build_redo", "build_clear", "build_tp" -> {
                int nl = p.arg().indexOf('\n');
                if (nl > 0) {
                    String id = p.arg().substring(0, nl), place = p.arg().substring(nl + 1);
                    switch (p.action()) {
                        case "build_redo" -> com.foredayner.scenario.town.BuildService.rebuild(player, id, place);
                        case "build_clear" -> com.foredayner.scenario.town.BuildService.clear(player, id, place);
                        default -> com.foredayner.scenario.town.BuildService.teleport(player, id, place);
                    }
                }
            }
            case "layout" -> com.foredayner.scenario.layout.LayoutService.make(player, p.arg());
            case "layout_auto" -> com.foredayner.scenario.layout.LayoutService.auto(player, p.arg());
            case "layout_revise" -> {
                int nl = p.arg().indexOf('\n');
                if (nl > 0) {
                    com.foredayner.scenario.layout.LayoutService.revise(player, p.arg().substring(0, nl), p.arg().substring(nl + 1));
                }
            }
            case "open" -> {
                try {
                    var sheet = com.foredayner.scenario.sheet.SheetService.load(p.arg());
                    if (sheet != null) {
                        sheet(player, p.arg(), sheet);
                    }
                    var layout = com.foredayner.scenario.layout.LayoutService.load(p.arg());
                    ServerPlayNetworking.send(player, new LayoutPayload(p.arg(), layout == null ? new byte[0] : gzip(layout.toJson())));
                    build(player, p.arg(), com.foredayner.scenario.town.BuildService.current(p.arg()),
                        com.foredayner.scenario.town.BuildService.canUndo(p.arg()));
                    status(player, "", p.arg());
                } catch (IOException e) {
                    status(player, "열지 못했어요: " + e.getMessage(), "");
                }
            }
            case "models" -> {
                com.foredayner.scenario.store.ScenarioConfig.set(com.foredayner.scenario.store.ScenarioConfig.fromJson(p.arg()));
                ServerPlayNetworking.send(player, new ConfigS2C(com.foredayner.scenario.store.ScenarioConfig.get().toJson()));
                status(player, "모델 설정을 저장했어요.", "");
            }
            default -> {
                ServerPlayNetworking.send(player, new ConfigS2C(com.foredayner.scenario.store.ScenarioConfig.get().toJson()));
                status(player, "", "");
            }
        }
    }

    public static void sheet(ServerPlayerEntity player, String id, com.foredayner.scenario.sheet.ScenarioSheet sheet) {
        ServerPlayNetworking.send(player, new SheetPayload(id, gzip(sheet.toJson())));
    }

    public static void build(ServerPlayerEntity player, String id, com.foredayner.scenario.town.BuildState state, boolean undo) {
        ServerPlayNetworking.send(player, new BuildPayload(id, gzip(state.toJson()), undo));
    }

    public static void layout(ServerPlayerEntity player, String id, com.foredayner.scenario.layout.TownLayout layout) {
        ServerPlayNetworking.send(player, new LayoutPayload(id, gzip(layout.toJson())));
    }

    /** The GM moved things on the layout: check it and save it (problems are reported, not blocked). */
    private static void saveLayout(ServerPlayerEntity player, LayoutPayload p) {
        if (!allowed(player)) {
            return;
        }
        try {
            var sheet = com.foredayner.scenario.sheet.SheetService.load(p.id());
            if (sheet == null) {
                status(player, "시트가 없어요.", p.id());
                return;
            }
            sheet.normalize();
            var layout = com.foredayner.scenario.layout.TownLayout.fromJson(gunzip(p.data()));
            java.util.List<String> problems = layout.check(sheet);
            com.foredayner.scenario.layout.LayoutService.save(p.id(), layout);
            layout(player, p.id(), layout);
            status(player, problems.isEmpty() ? "배치를 저장했어요." : "저장했지만 확인할 것이 있어요: " + problems.get(0), p.id());
        } catch (IOException | RuntimeException e) {
            status(player, "배치를 저장하지 못했어요: " + e.getMessage(), p.id());
        }
    }

    /** The GM edited the sheet: check it and save it. */
    private static void saveSheet(ServerPlayerEntity player, SheetPayload p) {
        if (!allowed(player)) {
            return;
        }
        try {
            var sheet = com.foredayner.scenario.sheet.ScenarioSheet.fromJson(gunzip(p.data()));
            java.util.List<String> problems = sheet.normalize();
            com.foredayner.scenario.sheet.SheetService.save(p.id(), sheet);
            sheet(player, p.id(), sheet);
            status(player, problems.isEmpty() ? "시트를 저장했어요." : "저장했지만 확인할 것이 있어요: " + problems.get(0), p.id());
        } catch (IOException | RuntimeException e) {
            status(player, "시트를 저장하지 못했어요: " + e.getMessage(), p.id());
        }
    }

    public static byte[] gzip(String text) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bytes)) {
                gz.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void receive(ServerPlayerEntity player, UploadC2S p) {
        if (!allowed(player)) {
            return;
        }
        if (p.total() <= 0 || p.total() > MAX_PARTS || p.index() < 0 || p.index() >= p.total() || p.data().length > PART_SIZE) {
            return;
        }
        Assembly a = UPLOADS.get(player.getUuid());
        if (a == null || a.id != p.id() || a.parts.length != p.total()) {
            a = new Assembly(p.id(), p.total());
            UPLOADS.put(player.getUuid(), a);
        }
        if (a.parts[p.index()] == null) {
            a.parts[p.index()] = p.data();
            a.received++;
        }
        if (a.received < p.total()) {
            return;
        }
        UPLOADS.remove(player.getUuid());
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] part : a.parts) {
            all.writeBytes(part);
        }
        try {
            String text = gunzip(all.toByteArray());
            ScenarioText.Loaded loaded = ScenarioText.fromText(p.name(), text);
            ScenarioStore.Info info = ScenarioStore.save(loaded.name(), loaded.text(), loaded.cut() || p.cut());
            ScenarioBuilderMod.LOG.info("Scenario '{}' saved ({} chars) by {}", info.name, info.chars, player.getName().getString());
            status(player, "\"" + info.name + "\" 저장했어요 (" + info.chars + "자)" + (info.cut ? ", 너무 길어 뒷부분을 잘랐어요" : "") + ".", info.id);
        } catch (IOException | RuntimeException e) {
            status(player, "시나리오를 받지 못했어요: " + e.getMessage(), "");
        }
    }

    /** At most 1 MB of text after unpacking. */
    public static String gunzip(byte[] data) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] out = in.readNBytes(1_000_001);
            if (out.length > 1_000_000) {
                throw new IOException("too large");
            }
            return new String(out, StandardCharsets.UTF_8);
        }
    }
}
