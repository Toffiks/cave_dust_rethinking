// SPDX-License-Identifier: MPL-2.0
package net.lizistired.cavedust;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Random;

@Mod(value = CaveDustMod.MOD_ID, dist = Dist.CLIENT)
public final class CaveDustClient {
    private static final Random RANDOM = new Random();
    private static final double SPAWN_RATE_INCREASE_PER_TICK = 5.0D;
    private static final double SPAWN_ACCUMULATOR_EPSILON = 1.0E-9D;
    private static final double MIN_MOVEMENT_SPEED_SQUARED = 0.0004D;
    private static final double MAX_SPAWN_OFFSET = 2.0D;
    private static final double SPAWN_OFFSET_SMOOTHING = 0.5D;
    private static final double MAX_VERTICAL_SPAWN_RADIUS = 3.0D;
    private static final double SPAWN_CENTER_HEIGHT = 1.4D;
    private static final double MIN_CAMERA_DISTANCE = 1.75D;
    private static final String CATEGORY = "key.category.cavedust.spook";
    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.cavedust.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, CATEGORY);
    private static final KeyMapping RELOAD_KEY = new KeyMapping(
            "key.cavedust.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ENTER, CATEGORY);

    private static CaveDustConfig config;
    private ClientLevel lastLevel;
    private double currentSpawnRate;
    private double spawnAccumulator;
    private double spawnOffsetX;
    private double spawnOffsetZ;

    public CaveDustClient(IEventBus modEventBus, ModContainer container) {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("cavedust.json");
        config = CaveDustConfig.load(configFile);

        modEventBus.addListener(this::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (ignored, parent) -> new CaveDustConfigScreen(parent));
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
        event.register(RELOAD_KEY);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        createCaveDust(Minecraft.getInstance());
    }

    private void createCaveDust(Minecraft client) {
        if (client.level != lastLevel) {
            resetSpawnRate();
            lastLevel = client.level;
        }

        if (client.player == null || client.level == null) {
            resetSpawnRate();
            CaveDustParticleContext.clear();
            return;
        }

        CaveDustParticleContext.update(client);
        LocalPlayer player = client.player;
        if (TOGGLE_KEY.consumeClick()) {
            boolean enabled = config.toggleEnabled();
            config.saveIfDirty();
            player.displayClientMessage(Component.translatable("debug.cavedust.toggle." + enabled), true);
        }
        if (RELOAD_KEY.consumeClick()) {
            config.reload();
            player.displayClientMessage(Component.translatable("debug.cavedust.reload"), true);
        }

        if (!shouldSpawnDust(client, player)) {
            resetSpawnRate();
            return;
        }

        double targetSpawnRate = depthFactor(player.getBlockY()) * config.particleMultiplier();
        if (targetSpawnRate <= 0.0D) {
            resetSpawnRate();
            return;
        }

        currentSpawnRate = Math.min(
                targetSpawnRate,
                currentSpawnRate + SPAWN_RATE_INCREASE_PER_TICK);
        spawnAccumulator += currentSpawnRate;
        int amount = (int) (spawnAccumulator + SPAWN_ACCUMULATOR_EPSILON);
        spawnAccumulator -= amount;
        if (spawnAccumulator < 0.0D) {
            spawnAccumulator = 0.0D;
        }
        double movementX = player.getDeltaMovement().x;
        double movementZ = player.getDeltaMovement().z;
        double movementSpeedSquared = movementX * movementX + movementZ * movementZ;
        double targetOffsetX = 0.0D;
        double targetOffsetZ = 0.0D;
        if (movementSpeedSquared > MIN_MOVEMENT_SPEED_SQUARED) {
            double offset = Math.min(MAX_SPAWN_OFFSET, config.width() * 0.2D)
                    / Math.sqrt(movementSpeedSquared);
            targetOffsetX = movementX * offset;
            targetOffsetZ = movementZ * offset;
        }
        spawnOffsetX += (targetOffsetX - spawnOffsetX) * SPAWN_OFFSET_SMOOTHING;
        spawnOffsetZ += (targetOffsetZ - spawnOffsetZ) * SPAWN_OFFSET_SMOOTHING;
        if (amount == 0) {
            return;
        }
        ParticleOptions particle = config.particle();
        SpriteSet sprites = particle == null ? localSprites(client, config.particleName()) : null;
        for (int i = 0; i < amount; i++) {
            spawnMote(client, player, particle, sprites);
        }
    }

    private void resetSpawnRate() {
        currentSpawnRate = 0.0D;
        spawnAccumulator = 0.0D;
        spawnOffsetX = 0.0D;
        spawnOffsetZ = 0.0D;
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
                && client.level.getLevelData().getHorizonHeight(client.level) == client.level.getMinBuildHeight()) {
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

    private void spawnMote(Minecraft client, LocalPlayer player,
                           ParticleOptions particle, SpriteSet sprites) {
        double radius = config.width() * Math.sqrt(RANDOM.nextDouble());
        double polar = Math.acos(2.0D * RANDOM.nextDouble() - 1.0D);
        double azimuth = Math.PI * 2.0D * RANDOM.nextDouble();
        double verticalScale = Math.min(config.width(), MAX_VERTICAL_SPAWN_RADIUS) / config.width();
        double x = player.getX() + spawnOffsetX + radius * Math.sin(polar) * Math.cos(azimuth);
        double y = player.getY() + SPAWN_CENTER_HEIGHT
                + radius * verticalScale * Math.sin(polar) * Math.sin(azimuth);
        double z = player.getZ() + spawnOffsetZ + radius * Math.cos(polar);

        double cameraX = x - player.getX();
        double cameraY = y - player.getEyeY();
        double cameraZ = z - player.getZ();
        double minimumDistance = Math.min(MIN_CAMERA_DISTANCE, config.width() * 0.5D);
        if (cameraX * cameraX + cameraY * cameraY + cameraZ * cameraZ
                < minimumDistance * minimumDistance) {
            return;
        }

        BlockPos particlePosition = BlockPos.containing(x, y, z);
        if (particlePosition.getY() < client.level.getMinBuildHeight()
                || particlePosition.getY() >= client.level.getMaxBuildHeight()) {
            return;
        }
        var state = client.level.getBlockState(particlePosition);
        if (!state.isAir() || !state.getFluidState().isEmpty()) {
            return;
        }

        if (particle != null) {
            client.level.addParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
        } else if (CaveDustConfig.PLUME_PARTICLE_ID.equals(config.particleName())) {
            client.particleEngine.add(new CaveDustPlumeParticle(client.level, x, y, z, 0.0D, 0.0D, 0.0D, sprites));
        } else {
            client.particleEngine.add(new CaveDustMoteParticle(client.level, x, y, z, 0.0D, 0.0D, 0.0D, sprites));
        }
    }

    private static SpriteSet localSprites(Minecraft client, String particleId) {
        String spriteName = CaveDustConfig.PLUME_PARTICLE_ID.equals(particleId) ? "big_smoke_0" : "generic_0";
        TextureAtlas atlas = (TextureAtlas) client.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        TextureAtlasSprite sprite = atlas.getSprite(ResourceLocation.parse("minecraft:" + spriteName));
        return new SpriteSet() {
            @Override public TextureAtlasSprite get(int age, int lifetime) { return sprite; }
            @Override public TextureAtlasSprite get(RandomSource random) { return sprite; }
            public TextureAtlasSprite first() { return sprite; }
        };
    }

    static CaveDustConfig config() {
        return config;
    }
}
