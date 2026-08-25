package net.lizistired.cavedust;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Publishes main-thread snapshots for particles and shares block-environment
 * checks between particles occupying the same block.
 */
final class CaveDustParticleContext {
    private static final int CACHE_LIFETIME_TICKS = 5;
    private static final int MAX_REQUESTS_PER_TICK = 2048;
    private static final EnvironmentSample EMPTY_ENVIRONMENT = new EnvironmentSample(false, false, 0.0D);
    private static final PlayerSnapshot NO_PLAYER = new PlayerSnapshot(
            false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    private static final Map<Long, CacheEntry> ENVIRONMENT = new ConcurrentHashMap<>();
    private static final Set<Long> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<EnvironmentRequest> REQUESTS = new ConcurrentLinkedQueue<>();

    private static volatile PlayerSnapshot playerSnapshot = NO_PLAYER;
    private static volatile long clientTick;
    private static ClientLevel activeLevel;

    private CaveDustParticleContext() {
    }

    static void update(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            clear();
            return;
        }

        if (activeLevel != level) {
            ENVIRONMENT.clear();
            REQUESTED.clear();
            REQUESTS.clear();
            activeLevel = level;
            clientTick = 0L;
        }

        clientTick++;
        Vec3 movement = player.getDeltaMovement();
        playerSnapshot = new PlayerSnapshot(
                true,
                player.getX(), player.getY(), player.getZ(),
                player.getX(), player.getEyeY(), player.getZ(),
                movement.x, movement.y, movement.z);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int processed = 0; processed < MAX_REQUESTS_PER_TICK; processed++) {
            EnvironmentRequest request = REQUESTS.poll();
            if (request == null) {
                break;
            }
            EnvironmentSample sample = sampleEnvironment(level, cursor, request.x(), request.y(), request.z());
            ENVIRONMENT.put(request.key(), new CacheEntry(sample, clientTick + CACHE_LIFETIME_TICKS));
            REQUESTED.remove(request.key());
        }

        if ((clientTick & 31L) == 0L) {
            long oldestUsefulTick = clientTick - CACHE_LIFETIME_TICKS * 4L;
            ENVIRONMENT.entrySet().removeIf(entry -> entry.getValue().expiresAt() < oldestUsefulTick);
        }
    }

    static void clear() {
        playerSnapshot = NO_PLAYER;
        activeLevel = null;
        clientTick = 0L;
        ENVIRONMENT.clear();
        REQUESTED.clear();
        REQUESTS.clear();
    }

    static PlayerSnapshot player() {
        return playerSnapshot;
    }

    static EnvironmentSample environmentAt(double x, double y, double z) {
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);
        long key = BlockPos.asLong(blockX, blockY, blockZ);
        CacheEntry entry = ENVIRONMENT.get(key);
        boolean expired = entry == null || entry.expiresAt() <= clientTick;
        if (expired) {
            if (REQUESTED.add(key)) {
                REQUESTS.offer(new EnvironmentRequest(key, blockX, blockY, blockZ));
            }
        }
        return expired ? EMPTY_ENVIRONMENT : entry.sample();
    }

    private static EnvironmentSample sampleEnvironment(ClientLevel level, BlockPos.MutableBlockPos cursor,
                                                       int x, int y, int z) {
        cursor.set(x, y, z);
        boolean seesSky = level.canSeeSky(cursor);
        double heatStrength = heatValueAt(level, cursor, x, y, z)
                + heatValueAt(level, cursor, x, y + 1, z) * 0.5D
                + heatValueAt(level, cursor, x, y - 1, z) * 0.5D
                + heatValueAt(level, cursor, x, y, z - 1) * 0.5D
                + heatValueAt(level, cursor, x, y, z + 1) * 0.5D
                + heatValueAt(level, cursor, x + 1, y, z) * 0.5D
                + heatValueAt(level, cursor, x - 1, y, z) * 0.5D;
        return new EnvironmentSample(true, seesSky, heatStrength);
    }

    private static double heatValueAt(ClientLevel level, BlockPos.MutableBlockPos cursor, int x, int y, int z) {
        cursor.set(x, y, z);
        BlockState state = level.getBlockState(cursor);
        if (state.is(Blocks.LAVA)) return 0.4D;
        if (state.is(Blocks.MAGMA_BLOCK)) return 0.2D;
        if (state.is(Blocks.CAMPFIRE)) return 0.25D;
        if (state.is(Blocks.SOUL_CAMPFIRE)) return 0.2D;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) return 0.1D;
        return 0.0D;
    }

    record PlayerSnapshot(boolean available,
                          double x, double y, double z,
                          double eyeX, double eyeY, double eyeZ,
                          double movementX, double movementY, double movementZ) {
    }

    record EnvironmentSample(boolean available, boolean seesSky, double heatStrength) {
    }

    private record EnvironmentRequest(long key, int x, int y, int z) {
    }

    private record CacheEntry(EnvironmentSample sample, long expiresAt) {
    }
}
