package net.lizistired.cavedust;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.Random;

@Mod.EventBusSubscriber(modid = CaveDustMod.MOD_ID, value = Dist.CLIENT)
public final class CaveDustClient {
    private static final Random RANDOM = new Random();
    private static final String CATEGORY = "key.category.cavedust.spook";
    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.cavedust.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ADD, CATEGORY);
    private static final KeyMapping RELOAD_KEY = new KeyMapping(
            "key.cavedust.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_ENTER, CATEGORY);

    private static CaveDustConfig config;

    private CaveDustClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && config != null) {
            createCaveDust(Minecraft.getInstance());
        }
    }

    private static void createCaveDust(Minecraft client) {
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

    private static boolean shouldSpawnDust(Minecraft client, LocalPlayer player) {
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

    private static double depthFactor(int y) {
        double upper = config.upperLimit();
        double lower = config.lowerLimit();
        double normalized = 1.0D - ((y - lower) / (upper - lower));
        return Math.max(0.0D, Math.min(1.0D, normalized));
    }

    private static void spawnMote(Minecraft client, BlockPos origin, ParticleOptions particle) {
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

    static CaveDustConfig config() {
        return config;
    }

    @Mod.EventBusSubscriber(modid = CaveDustMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            Path configFile = FMLPaths.CONFIGDIR.get().resolve("cavedust.json");
            config = CaveDustConfig.load(configFile);
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_KEY);
            event.register(RELOAD_KEY);
        }

        @SubscribeEvent
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(CaveDustMod.CAVE_DUST_MOTE.get(), CaveDustMoteParticle.Provider::new);
            event.registerSpriteSet(CaveDustMod.CAVE_DUST_PLUME.get(), CaveDustPlumeParticle.Provider::new);
        }
    }
}
