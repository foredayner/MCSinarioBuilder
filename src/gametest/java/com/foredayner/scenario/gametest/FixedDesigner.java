package com.foredayner.scenario.gametest;

import com.example.ai.AiController;
import com.example.api.AiBuildApi;
import com.foredayner.scenario.town.BuildService;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Stands in for the AI in tests: every building is the same plain house sized to its lot, with
 * its door on the front and a bookshelf at each spot it was asked to mark; props are refused (so
 * outdoor places keep only their ground).
 */
public final class FixedDesigner implements BuildService.Designer {

    @Override
    public CompletableFuture<AiBuildApi.Design> building(ServerWorld world, BlockPos near, AiBuildApi.Site site, AiBuildApi.BuildingRequest req) {
        if (req.description().startsWith("outdoor object")) {
            return CompletableFuture.completedFuture(new AiBuildApi.Design(false, "no props in tests", null, "", 0, 0, 0));
        }
        int w = site.width(), d = site.depth();
        int h = Math.min(site.maxHeight() - 2, 6);
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.ROOT, "ai.placeBox(1, 0, -%d, %d, %d, %d, 'spruce_planks')\n", d - 1, w - 2, h, d - 1));
        s.append(String.format(Locale.ROOT, "ai.placeBox(0, %d, -%d, %d, 1, %d, 'dark_oak_planks')\n", h, d, w, d));
        s.append(String.format(Locale.ROOT, "ai.placeBlock(%d, 1, -1, 'spruce_door[facing=south,half=lower]')\n", w / 2));
        s.append(String.format(Locale.ROOT, "ai.placeBlock(%d, 2, -1, 'spruce_door[facing=south,half=upper]')\n", w / 2));
        s.append(String.format(Locale.ROOT, "ai.placeBlock(%d, 2, -%d, 'glass_pane')\n", 2, d - 1));
        int i = 0;
        for (String mark : req.marks() == null ? java.util.List.<String>of() : req.marks()) {
            String name = mark.trim().split(" ")[0];
            int x = 2 + i % Math.max(1, w - 4), z = -2 - (i / Math.max(1, w - 4)) % Math.max(1, d - 4);
            s.append(String.format(Locale.ROOT, "ai.placeBlock(%d, 1, %d, 'bookshelf')\n", x, z));
            s.append(String.format(Locale.ROOT, "ai.mark('%s', %d, 1, %d)\n", name.replace("'", ""), x, z));
            i++;
        }
        var eval = AiController.evaluate(world, near, 180f, null, s.toString(), false);
        if (!eval.ok()) {
            return CompletableFuture.completedFuture(new AiBuildApi.Design(false, eval.failure(), null, s.toString(), 0, 0, 0));
        }
        return CompletableFuture.completedFuture(new AiBuildApi.Design(true, null, eval.plan(), s.toString(), 1, 0, 0));
    }

    @Override
    public CompletableFuture<AiBuildApi.Design> object(ServerWorld world, BlockPos near, String description, double maxSize, String model) {
        return CompletableFuture.completedFuture(new AiBuildApi.Design(false, "no props in tests", null, "", 0, 0, 0));
    }
}
