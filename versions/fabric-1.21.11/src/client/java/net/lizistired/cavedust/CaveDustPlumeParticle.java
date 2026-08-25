package net.lizistired.cavedust;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

final class CaveDustPlumeParticle extends SingleQuadParticle {
    CaveDustPlumeParticle(ClientLevel level, double x, double y, double z,
                          double velocityX, double velocityY, double velocityZ,
                          SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.lifetime = 60;
        this.quadSize = 10.0F;
        this.xd = velocityX;
        this.yd = -0.007000000216066837D;
        this.zd = velocityZ;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasPhysics = false;
        this.alpha = 0.5F;
    }

    @Override
    public void tick() {
        super.tick();
        CaveDustParticleContext.PlayerSnapshot player = CaveDustParticleContext.player();
        if (!player.available()) {
            return;
        }
        double distance = Math.sqrt(square(this.x - player.eyeX())
                + square(this.y - player.eyeY())
                + square(this.z - player.eyeZ()));
        this.alpha = Math.max(-1.0F, Math.min(1.0F, (float) (distance / 50.0D - 1.0D)));
        if (this.alpha < 0.001F) {
            this.remove();
            return;
        }
        this.alpha -= 0.00005F;
    }

    private static double square(double value) {
        return value * value;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ,
                                       RandomSource random) {
            return new CaveDustPlumeParticle(level, x, y, z, velocityX, velocityY, velocityZ, sprites);
        }
    }
}
