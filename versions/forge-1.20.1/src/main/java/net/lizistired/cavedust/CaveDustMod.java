package net.lizistired.cavedust;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(CaveDustMod.MOD_ID)
public final class CaveDustMod {
    public static final String MOD_ID = "cavedust";

    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);

    public static final RegistryObject<SimpleParticleType> CAVE_DUST_MOTE =
            PARTICLE_TYPES.register("cave_dust_mote", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CAVE_DUST_PLUME =
            PARTICLE_TYPES.register("cave_dust_plume", () -> new SimpleParticleType(false));

    public CaveDustMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        PARTICLE_TYPES.register(modEventBus);
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new CaveDustConfigScreen(parent)));
    }
}
