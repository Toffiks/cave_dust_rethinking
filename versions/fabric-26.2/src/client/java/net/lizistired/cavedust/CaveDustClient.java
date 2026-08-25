package net.lizistired.cavedust;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Random;

/** Client-side implementation of Cave Dust Rethinking for Minecraft 26.2. */
public final class CaveDustClient implements ClientModInitializer {
    public static final String MOD_ID = CaveDustMod.MOD_ID;

    private static final Random RANDOM = new Random();
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(id("spook"));
    private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.cavedust.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, CATEGORY));
    private static final KeyMapping RELOAD_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.cavedust.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ENTER, CATEGORY));

    private static CaveDustConfig config;

    @Override
    public void onInitializeClient() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("cavedust.json");
        config = CaveDustConfig.load(configFile);
        ParticleProviderRegistry.getInstance().register(CaveDustMod.CAVE_DUST_MOTE, CaveDustMoteParticle.Provider::new);
        ParticleProviderRegistry.getInstance().register(CaveDustMod.CAVE_DUST_PLUME, CaveDustPlumeParticle.Provider::new);
        ClientTickEvents.END_CLIENT_TICK.register(this::createCaveDust);
    }

    private void createCaveDust(Minecraft client) {
        if (client.player == null || client.level == null) {
            CaveDustParticleContext.clear();
            return;
        }

        CaveDustParticleContext.update(client);
        LocalPlayer player = client.player;
        if (TOGGLE_KEY.consumeClick()) {
            boolean enabled = config.toggleEnabled();
            config.saveIfDirty();
            player.sendOverlayMessage(Component.translatable("debug.cavedust.toggle." + enabled));
        }
        if (RELOAD_KEY.consumeClick()) {
            config.reload();
            player.sendOverlayMessage(Component.translatable("debug.cavedust.reload"));
        }

        if (!shouldSpawnDust(client, player)) {
            return;
        }

        double depth = depthFactor(player.getBlockY());
        int amount = (int) (depth * config.particleMultiplier());
        BlockPos origin = player.blockPosition();
        ParticleOptions particle = config.particle();
        for (int i = 0; i < amount; i++) {
            spawnMote(client, origin, particle);
        }
    }

    private boolean shouldSpawnDust(Minecraft client, LocalPlayer player) {
        if (!config.enabled() || client.isPaused() || player.isUnderWater()) {
            return false;
        }
        if (client.level.dimensionType().hasFixedTime()) {
            return false;
        }

        BlockPos position = player.blockPosition();
        if (!config.superflatEnabled()
                && client.level.getLevelData().getHorizonHeight(client.level) == client.level.getMinY()) {
            return false;
        }
        if (client.level.canSeeSky(position)) {
            return false;
        }
        if (config.seaLevelCheck() && position.getY() + 2 >= client.level.getSeaLevel()) {
            return false;
        }
        return !client.level.getBiome(position).is(net.minecraft.world.level.biome.Biomes.LUSH_CAVES);
    }

    private double depthFactor(int y) {
        double upper = config.upperLimit();
        double lower = config.lowerLimit();
        double normalized = 1.0D - ((y - lower) / (upper - lower));
        return Math.max(0.0D, Math.min(1.0D, normalized));
    }

    private void spawnMote(Minecraft client, BlockPos origin, ParticleOptions particle) {
        double radius = config.width() * Math.pow(RANDOM.nextDouble(), 0.2D);
        double polar = Math.acos(2.0D * RANDOM.nextDouble() - 1.0D);
        double azimuth = Math.PI * 2.0D * RANDOM.nextDouble();

        int spawnX = origin.getX() + (int) (radius * Math.sin(polar) * Math.cos(azimuth));
        int spawnY = origin.getY() + (int) (radius * Math.sin(polar) * Math.sin(azimuth));
        int spawnZ = origin.getZ() + (int) (radius * Math.cos(polar));
        double x = spawnX + RANDOM.nextFloat();
        double y = spawnY + RANDOM.nextFloat();
        double z = spawnZ + RANDOM.nextFloat();

        BlockPos particlePosition = BlockPos.containing(x, y, z);
        var state = client.level.getBlockState(particlePosition);
        if (!state.isAir() || !state.getFluidState().isEmpty()) {
            return;
        }

        client.level.addParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    static CaveDustConfig config() {
        return config;
    }
}
