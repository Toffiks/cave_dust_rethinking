package net.lizistired.cavedust;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

final class CaveDustMoteParticle extends TextureSheetParticle {
    private static final int ENVIRONMENT_CHECK_INTERVAL = 5;

    private final SpriteSet sprites;
    private int environmentCheckDelay;
    private boolean cachedSeesSky;
    private double cachedHeatStrength;

    private CaveDustMoteParticle(ClientLevel level, double x, double y, double z,
                                 double velocityX, double velocityY, double velocityZ,
                                 SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 200;
        this.quadSize = 0.05F;
        this.xd = velocityX;
        this.yd = -0.007000000216066837D;
        this.zd = velocityZ;
        this.hasPhysics = false;
        this.alpha = 1.0F;
        this.setSpriteFromAge(sprites);
        this.environmentCheckDelay = this.random.nextInt(ENVIRONMENT_CHECK_INTERVAL);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        this.alpha = 1.0F - (float) this.age / (float) this.lifetime;

        CaveDustParticleContext.PlayerSnapshot player = CaveDustParticleContext.player();
        if (player.available()) {
            double distanceSquared = square(this.x - player.x())
                    + square(this.y - player.y())
                    + square(this.z - player.z());
            if (distanceSquared < 9.0D) {
                double distance = Math.sqrt(distanceSquared);
                double influence = (1.0D - distance / 3.0D) * 0.1D;
                this.xd += player.movementX() * influence;
                this.yd += player.movementY() * influence * 0.2D;
                this.zd += player.movementZ() * influence;
            }
        }

        if (environmentCheckDelay-- <= 0) {
            CaveDustParticleContext.EnvironmentSample environment =
                    CaveDustParticleContext.environmentAt(this.x, this.y, this.z);
            if (environment.available()) {
                cachedSeesSky = environment.seesSky();
                cachedHeatStrength = environment.heatStrength();
                environmentCheckDelay = ENVIRONMENT_CHECK_INTERVAL - 1;
            } else {
                environmentCheckDelay = 0;
            }
        }

        if (cachedSeesSky) {
            this.xd *= 0.98D;
            this.zd *= 0.98D;
            this.yd -= 0.01D;
        } else {
            this.xd *= 0.995D;
            this.yd += (this.random.nextFloat() - 0.5F) * 0.002F;
            this.zd *= 0.995D;
        }
        this.xd += (this.random.nextFloat() - 0.5F) * 0.002F;
        this.zd += (this.random.nextFloat() - 0.5F) * 0.002F;

        if (cachedHeatStrength > 0.0D) {
            double lift = Math.min(cachedHeatStrength * 0.02D, 0.03D);
            this.yd = this.yd * 0.9D + lift * 0.1D;
            this.xd += (this.random.nextFloat() - 0.5F) * lift * 0.3D;
            this.zd += (this.random.nextFloat() - 0.5F) * lift * 0.3D;
        }
        this.yd = Math.min(this.yd, 0.05D);
    }

    private static double square(double value) {
        return value * value;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new CaveDustMoteParticle(level, x, y, z, velocityX, velocityY, velocityZ, sprites);
        }
    }
}
