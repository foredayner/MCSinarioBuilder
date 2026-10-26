package com.foredayner.scenario.layout;

import com.example.ai.AiImage;
import com.foredayner.scenario.net.ScenarioNet;
import com.foredayner.scenario.sheet.ScenarioSheet;
import com.foredayner.scenario.sheet.SheetService;
import com.foredayner.scenario.store.ScenarioStore;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The keeper's map of the town: a picture (a file, or a web address that is downloaded once) kept
 * with the scenario. The AI lays the town out after it, and a small copy lies under the layout so
 * the keeper can compare.
 */
public final class MapService {

    /** The "place" a map upload is sent for (picture uploads share one message). */
    public static final String UPLOAD_KEY = "#map";
    private static final int MAX_BYTES = 4_500_000;
    private static final String THUMB = "map_thumb.png";

    private MapService() {
    }

    static Path dir(String id) throws IOException {
        Path d = ScenarioStore.dir(id).resolve("refs");
        Files.createDirectories(d);
        return d;
    }

    /** The map for the AI, or null when the scenario has none. */
    public static AiImage image(String id, ScenarioSheet sheet) throws IOException {
        if (sheet == null || sheet.mapImage == null || !sheet.mapImage.startsWith("file:")) {
            return null;
        }
        String name = sheet.mapImage.substring(5);
        if (!name.matches("map\\.(png|jpg|gif|webp)")) {
            throw new IOException("bad map file name");
        }
        Path f = dir(id).resolve(name);
        return Files.exists(f) ? AiImage.ofBytes(Files.readAllBytes(f)) : null;
    }

    /** The small copy under the layout (PNG), or an empty array. */
    public static byte[] thumbnail(String id) {
        try {
            Path f = dir(id).resolve(THUMB);
            return Files.exists(f) ? Files.readAllBytes(f) : new byte[0];
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** Keep a map picture sent by the keeper. */
    public static void saveFile(ServerPlayerEntity player, String id, byte[] data) {
        try {
            AiImage checked = AiImage.ofBytes(data); // refuses what is not a picture or too big
            String ext = switch (checked.mediaType()) {
                case "image/png" -> "png";
                case "image/gif" -> "gif";
                case "image/webp" -> "webp";
                default -> "jpg";
            };
            ScenarioSheet sheet = SheetService.load(id);
            if (sheet == null) {
                say(player, "먼저 시트를 만들어 주세요.", id);
                return;
            }
            removeFiles(id);
            Files.write(dir(id).resolve("map." + ext), data);
            byte[] thumb = MapImages.thumbnail(data);
            if (thumb != null) {
                Files.write(dir(id).resolve(THUMB), thumb);
            }
            sheet.mapImage = "file:map." + ext;
            SheetService.save(id, sheet);
            if (player != null) {
                ScenarioNet.sheet(player, id, sheet);
                ScenarioNet.map(player, id, thumb == null ? new byte[0] : thumb);
            }
            say(player, "참고 지도를 올렸어요" + (thumb == null ? " (이 형식은 배치도 밑에 겹쳐 보이지 않아요)" : "")
                + ". \"AI로 배치\"를 누르면 지도를 따라 배치해요.", id);
        } catch (IOException | RuntimeException e) {
            say(player, "지도를 저장하지 못했어요: " + e.getMessage(), id);
        }
    }

    /** Download a map from a web address, then keep it like a file. */
    public static void setUrl(ServerPlayerEntity player, String id, String url) {
        String u = url == null ? "" : url.trim();
        if (!u.startsWith("https://") && !u.startsWith("http://")) {
            say(player, "지도 주소는 https:// 로 시작해야 해요.", id);
            return;
        }
        say(player, "지도를 내려받는 중이에요...", id);
        try {
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "MCSinarioBuilder/0.1 (Minecraft mod)").GET().build();
            http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
                var server = player.getServer();
                if (server == null) {
                    return;
                }
                server.execute(() -> {
                    if (error != null || response == null || response.statusCode() != 200) {
                        say(player, "지도를 내려받지 못했어요: " + (error != null ? error.getMessage() : response == null ? "응답 없음"
                            : "HTTP " + response.statusCode()), id);
                    } else if (response.body().length > MAX_BYTES) {
                        say(player, "지도가 너무 커요 (" + response.body().length / 1000 + " KB, 4500 KB까지).", id);
                    } else {
                        saveFile(player, id, response.body());
                    }
                });
            });
        } catch (RuntimeException e) {
            say(player, "지도 주소가 이상해요: " + e.getMessage(), id);
        }
    }

    /** Take the map away. */
    public static void clear(ServerPlayerEntity player, String id) {
        try {
            removeFiles(id);
            ScenarioSheet sheet = SheetService.load(id);
            if (sheet != null) {
                sheet.mapImage = "";
                SheetService.save(id, sheet);
                if (player != null) {
                    ScenarioNet.sheet(player, id, sheet);
                }
            }
            if (player != null) {
                ScenarioNet.map(player, id, new byte[0]);
            }
            say(player, "참고 지도를 지웠어요.", id);
        } catch (IOException e) {
            say(player, "지도를 지우지 못했어요: " + e.getMessage(), id);
        }
    }

    private static void removeFiles(String id) throws IOException {
        for (String name : new String[] { "map.png", "map.jpg", "map.gif", "map.webp", THUMB }) {
            Files.deleteIfExists(dir(id).resolve(name));
        }
    }

    private static void say(ServerPlayerEntity player, String text, String id) {
        if (player != null) {
            ScenarioNet.status(player, text, id);
        }
    }
}
