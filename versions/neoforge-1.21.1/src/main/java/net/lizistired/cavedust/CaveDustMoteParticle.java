package net.lizistired.cavedust;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class CaveDustMoteParticle extends TextureSheetParticle {
    private static final int ENVIRONMENT_CHECK_INTERVAL = 5;

    private final SpriteSet sprites;
    private final BlockPos.MutableBlockPos environmentCursor = new BlockPos.MutableBlockPos();
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

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            double distanceSquared = square(this.x - player.getX())
                    + square(this.y - player.getY())
                    + square(this.z - player.getZ());
            if (distanceSquared < 9.0D) {
                double distance = Math.sqrt(distanceSquared);
                double influence = (1.0D - distance / 3.0D) * 0.1D;
                Vec3 movement = player.getDeltaMovement();
                this.xd += movement.x * influence;
                this.yd += movement.y * influence * 0.2D;
                this.zd += movement.z * influence;
            }
        }

        if (environmentCheckDelay-- <= 0) {
            updateEnvironmentCache();
            environmentCheckDelay = ENVIRONMENT_CHECK_INTERVAL - 1;
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

    private void updateEnvironmentCache() {
        int blockX = (int) Math.floor(this.x);
        int blockY = (int) Math.floor(this.y);
        int blockZ = (int) Math.floor(this.z);
        environmentCursor.set(blockX, blockY, blockZ);
        cachedSeesSky = this.level.canSeeSky(environmentCursor);

        cachedHeatStrength = heatValueAt(blockX, blockY, blockZ)
                + heatValueAt(blockX, blockY + 1, blockZ) * 0.5D
                + heatValueAt(blockX, blockY - 1, blockZ) * 0.5D
                + heatValueAt(blockX, blockY, blockZ - 1) * 0.5D
                + heatValueAt(blockX, blockY, blockZ + 1) * 0.5D
                + heatValueAt(blockX + 1, blockY, blockZ) * 0.5D
                + heatValueAt(blockX - 1, blockY, blockZ) * 0.5D;
    }

    private double heatValueAt(int x, int y, int z) {
        environmentCursor.set(x, y, z);
        BlockState state = this.level.getBlockState(environmentCursor);
        if (state.is(Blocks.LAVA)) return 0.4D;
        if (state.is(Blocks.MAGMA_BLOCK)) return 0.2D;
        if (state.is(Blocks.CAMPFIRE)) return 0.25D;
        if (state.is(Blocks.SOUL_CAMPFIRE)) return 0.2D;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) return 0.1D;
        return 0.0D;
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
