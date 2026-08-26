package net.lizistired.cavedust;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Random;

@Mod(value = CaveDustMod.MOD_ID, dist = Dist.CLIENT)
public final class CaveDustClient {
    private static final Random RANDOM = new Random();
    private static final String CATEGORY = "key.category.cavedust.spook";
    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.cavedust.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, CATEGORY);
    private static final KeyMapping RELOAD_KEY = new KeyMapping(
            "key.cavedust.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ENTER, CATEGORY);

    private static CaveDustConfig config;

    public CaveDustClient(IEventBus modEventBus, ModContainer container) {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("cavedust.json");
        config = CaveDustConfig.load(configFile);

        modEventBus.addListener(this::registerKeyMappings);
        modEventBus.addListener(this::registerParticleProviders);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (ignored, parent) -> new CaveDustConfigScreen(parent));
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
        event.register(RELOAD_KEY);
    }

    private void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CaveDustMod.CAVE_DUST_MOTE.get(), CaveDustMoteParticle.Provider::new);
        event.registerSpriteSet(CaveDustMod.CAVE_DUST_PLUME.get(), CaveDustPlumeParticle.Provider::new);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        createCaveDust(Minecraft.getInstance());
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
            player.displayClientMessage(Component.translatable("debug.cavedust.toggle." + enabled), true);
        }
        if (RELOAD_KEY.consumeClick()) {
            config.reload();
            player.displayClientMessage(Component.translatable("debug.cavedust.reload"), true);
        }

        if (!shouldSpawnDust(client, player)) {
            return;
        }

        int amount = (int) (depthFactor(player.getBlockY()) * config.particleMultiplier());
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

    private void spawnMote(Minecraft client, BlockPos origin, ParticleOptions particle) {
        double radius = config.width() * Math.pow(RANDOM.nextDouble(), 0.2D);
        double polar = Math.acos(2.0D * RANDOM.nextDouble() - 1.0D);
        double azimuth = Math.PI * 2.0D * RANDOM.nextDouble();

        int spawnX = origin.getX() + (int) (radius * Math.sin(polar) * Math.cos(azimuth));
        int spawnY = origin.getY() + (int) (radius * Math.sin(polar) * Math.sin(azimuth));
        int spawnZ = origin.getZ() + (int) (radius * Math.cos(polar));
        if (spawnY < client.level.getMinBuildHeight() || spawnY >= client.level.getMaxBuildHeight()) {
            return;
        }
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

    static CaveDustConfig config() {
        return config;
    }
}
