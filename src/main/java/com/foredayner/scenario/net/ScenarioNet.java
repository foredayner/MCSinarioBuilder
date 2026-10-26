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
            default -> status(player, "", "");
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
    static String gunzip(byte[] data) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            byte[] out = in.readNBytes(1_000_001);
            if (out.length > 1_000_000) {
                throw new IOException("too large");
            }
            return new String(out, StandardCharsets.UTF_8);
        }
    }
}
