package com.foredayner.scenario.content;

import com.foredayner.scenario.ScenarioBuilderMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** Items this mod adds: the GM handbook (scene list, teleports, clues, time and weather). */
public final class ScenarioContent {

    public static final Identifier GM_HANDBOOK_ID = Identifier.of(ScenarioBuilderMod.MOD_ID, "gm_handbook");
    public static Item GM_HANDBOOK;

    private ScenarioContent() {
    }

    public static void register() {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, GM_HANDBOOK_ID);
        GM_HANDBOOK = Registry.register(Registries.ITEM, key, new Item(new Item.Settings().registryKey(key).maxCount(1)));
    }
}
