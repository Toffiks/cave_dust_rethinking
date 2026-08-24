package net.lizistired.cavedust;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

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
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Vec3 position = new Vec3(this.x, this.y, this.z);
        double distance = position.distanceTo(player.getEyePosition());
        this.alpha = Math.max(-1.0F, Math.min(1.0F, (float) (distance / 50.0D - 1.0D)));
        if (this.alpha < 0.001F) {
            this.remove();
        }
        this.alpha -= 0.00005F;
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
