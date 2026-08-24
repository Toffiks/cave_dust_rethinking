package net.lizistired.cavedust;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CaveDustMod.MOD_ID)
public final class CaveDustMod {
    public static final String MOD_ID = "cavedust";

    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CAVE_DUST_MOTE =
            PARTICLE_TYPES.register("cave_dust_mote", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CAVE_DUST_PLUME =
            PARTICLE_TYPES.register("cave_dust_plume", () -> new SimpleParticleType(false));

    public CaveDustMod(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
