package com.foredayner.scenario.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** K opens the AI Scenario Workshop (operators only). */
public final class ScenarioClient implements ClientModInitializer {

    /** Same permission as the AI Build Assistant's workshop. */
    public static final int OP_LEVEL = 2;
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        ScenarioClientState.register();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.scenario-builder.open", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.scenario-builder"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (isOperator(client)) {
                    ScenarioClientState.requestList();
                    client.setScreen(new ScenarioScreen());
                }
            }
        });
    }

    static boolean isOperator(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        if (client.player.hasPermissionLevel(OP_LEVEL)) {
            return true;
        }
        client.player.sendMessage(Text.literal("관리자(OP)만 사용할 수 있어요."), true);
        return false;
    }
}
