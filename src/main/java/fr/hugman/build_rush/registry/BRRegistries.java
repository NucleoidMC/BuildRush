package fr.hugman.build_rush.registry;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public class BRRegistries {
    public static final ResourceKey<Registry<Build>> BUILD = ResourceKey.createRegistryKey(BuildRush.id("build"));

    public static void register() {
        DynamicRegistries.register(BUILD, Build.CODEC);
    }
}
