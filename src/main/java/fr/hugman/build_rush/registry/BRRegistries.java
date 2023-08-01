package fr.hugman.build_rush.registry;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.build.Build;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

public class BRRegistries {
    public static final RegistryKey<Registry<Build>> BUILD = RegistryKey.ofRegistry(BuildRush.id("build"));

    public static void register() {
        DynamicRegistries.register(BUILD, Build.CODEC);
    }
}
