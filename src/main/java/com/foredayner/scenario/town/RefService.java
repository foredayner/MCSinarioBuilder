package com.foredayner.scenario.town;

import com.example.ai.AiImage;
import com.example.ai.AiPrompt;
import com.example.api.AiBuildApi;
import com.foredayner.scenario.net.ScenarioNet;
import com.foredayner.scenario.sheet.ScenarioSheet;
import com.foredayner.scenario.sheet.SheetService;
import com.foredayner.scenario.store.ScenarioConfig;
import com.foredayner.scenario.store.ScenarioStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A place's reference picture: the keeper gives a photo of a real building (a web address or a
 * file); the AI looks at it once and writes building directions from it, which every design of
 * that place then follows.
 */
public final class RefService {

    private RefService() {
    }

    static final String SYSTEM = """
        You look at a photo of a real building. A Minecraft builder will build a building like it on a site of the
        given size (1 block = 1 metre). Describe it for that builder in English, 10-18 short lines, plain text:
        - overall shape, number of storeys, proportions (width : height), roof shape
        - the front from bottom to top: what is on each floor (entrance, shop glass, windows, balconies, signs)
        - windows: shape, size, rhythm; the entrance: doors, canopy, steps
        - signs and colour bands: colours and where they are (describe brands and logos as plain coloured bands,
          never name them; no readable text)
        - materials and colours, each mapped to Minecraft blocks (e.g. light grey tile -> smooth_quartz or
          light_gray_concrete)
        - distinctive details (lights, flag poles, railings, roof tanks, pipes, cameras)
        - what is around it (fence, parking, trees)
        Ignore people, cars and anything not part of the building.
        """;

    /** Files of a scenario's pictures. */
    static Path dir(String id) throws IOException {
        Path d = ScenarioStore.dir(id).resolve("refs");
        Files.createDirectories(d);
        return d;
    }

    /** The picture of a place, ready to send. */
    static AiImage image(String id, String ref) throws IOException {
        if (ref.startsWith("file:")) {
            String name = ref.substring(5);
            if (!name.matches("[0-9a-zA-Z가-힣_.-]{1,80}")) {
                throw new IOException("bad file name");
            }
            return AiImage.ofBytes(Files.readAllBytes(dir(id).resolve(name)));
        }
        if (!ref.startsWith("https://") && !ref.startsWith("http://")) {
            throw new IOException("not a web address (https://...)");
        }
        return AiImage.ofUrl(ref);
    }

    /** Keep a picture file sent by the keeper; returns its reference ("file:<name>"). */
    public static String saveFile(String id, String placeId, byte[] data) throws IOException {
        AiImage checked = AiImage.ofBytes(data); // refuses what is not a picture or too big
        String ext = switch (checked.mediaType()) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String name = ScenarioSheet.slug(placeId).replaceAll("[^0-9a-z가-힣_]", "") + "." + ext;
        Files.write(dir(id).resolve(name), data);
        return "file:" + name;
    }

    /** Set (or clear, with "") a place's picture and have the AI describe it. */
    public static void set(MinecraftServer server, ServerPlayerEntity player, String id, String placeId, String ref, Consumer<Boolean> then) {
        try {
            ScenarioSheet sheet = SheetService.load(id);
            ScenarioSheet.Place place = sheet == null ? null : sheet.place(placeId);
            if (place == null) {
                say(player, "그런 장소가 없어요.", id);
                then.accept(false);
                return;
            }
            place.refImage = ref == null ? "" : ref.trim();
            place.refDescription = "";
            SheetService.save(id, sheet);
            if (player != null) {
                ScenarioNet.sheet(player, id, sheet);
            }
            if (place.refImage.isEmpty()) {
                say(player, place.name + ": 참고 사진을 지웠어요.", id);
                then.accept(true);
                return;
            }
            describe(server, player, id, placeId, then);
        } catch (IOException e) {
            say(player, "참고 사진을 저장하지 못했어요: " + e.getMessage(), id);
            then.accept(false);
        }
    }

    /** Have the AI look at the place's picture and keep its description in the sheet. */
    public static void describe(MinecraftServer server, ServerPlayerEntity player, String id, String placeId, Consumer<Boolean> then) {
        try {
            ScenarioSheet sheet = SheetService.load(id);
            ScenarioSheet.Place place = sheet == null ? null : sheet.place(placeId);
            if (place == null || place.refImage.isBlank()) {
                then.accept(false);
                return;
            }
            AiImage image = image(id, place.refImage);
            int size = ScenarioSheet.footprint(place.size);
            String user = String.format(Locale.ROOT, "The site is %d x %d blocks, up to %d storeys. It will be: %s \"%s\" (%s).\n"
                + "Describe the building in the photo now.", size, size, Math.max(place.floors, 1), place.kind, place.name, sheet.era);
            say(player, place.name + ": AI가 참고 사진을 보는 중이에요...", id);
            AiBuildApi.ask(server, new AiPrompt(SYSTEM, user), ScenarioConfig.buildingModel(), List.of(image)).thenAccept(answer -> {
                if (!answer.ok() || answer.text().strip().length() < 80) {
                    say(player, place.name + ": 사진을 읽지 못했어요: " + (answer.ok() ? "답이 너무 짧아요" : answer.error()), id);
                    then.accept(false);
                    return;
                }
                try {
                    ScenarioSheet fresh = SheetService.load(id);
                    ScenarioSheet.Place p = fresh.place(placeId);
                    p.refDescription = answer.text().replace("```", "").strip();
                    SheetService.save(id, fresh);
                    if (player != null) {
                        ScenarioNet.sheet(player, id, fresh);
                    }
                    say(player, place.name + ": 참고 사진을 읽었어요. 지을 때 따라 해요 (토큰 입력 " + answer.inputTokens() + " / 출력 "
                        + answer.outputTokens() + ").", id);
                    then.accept(true);
                } catch (IOException e) {
                    say(player, "저장하지 못했어요: " + e.getMessage(), id);
                    then.accept(false);
                }
            });
        } catch (IOException | RuntimeException e) {
            say(player, "참고 사진을 읽지 못했어요: " + e.getMessage(), id);
            then.accept(false);
        }
    }

    private static void say(ServerPlayerEntity player, String text, String id) {
        if (player != null) {
            ScenarioNet.status(player, text, id);
        }
    }
}
