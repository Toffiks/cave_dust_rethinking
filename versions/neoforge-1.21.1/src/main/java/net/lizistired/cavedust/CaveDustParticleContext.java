package net.lizistired.cavedust;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes main-thread snapshots for particles and shares block-environment
 * checks between particles occupying the same block.
 */
final class CaveDustParticleContext {
    private static final int CACHE_LIFETIME_TICKS = 5;
    private static final int MAX_REQUESTS_PER_TICK = 2048;
    private static final int MAX_QUEUED_REQUESTS = MAX_REQUESTS_PER_TICK * 2;
    private static final EnvironmentSample EMPTY_ENVIRONMENT = new EnvironmentSample(false, false, 0.0D);
    private static final PlayerSnapshot NO_PLAYER = new PlayerSnapshot(
            false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    private static final Map<Long, CacheEntry> ENVIRONMENT = new ConcurrentHashMap<>();
    private static final Set<EnvironmentRequestKey> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final Queue<EnvironmentRequest> REQUESTS = new ArrayBlockingQueue<>(MAX_QUEUED_REQUESTS);

    private static volatile PlayerSnapshot playerSnapshot = NO_PLAYER;
    private static volatile long clientTick;
    private static volatile long sessionGeneration;
    private static volatile boolean sessionActive;
    private static volatile int minimumBuildHeight = Integer.MAX_VALUE;
    private static volatile int maximumBuildHeight = Integer.MIN_VALUE;
    private static volatile ClientLevel activeLevel;

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
            startSession(level);
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
            if (request.key().sessionGeneration() != sessionGeneration) {
                REQUESTED.remove(request.key());
                continue;
            }
            EnvironmentSample sample = sampleEnvironment(level, cursor, request.x(), request.y(), request.z());
            ENVIRONMENT.put(request.key().blockPosition(), new CacheEntry(sample, clientTick + CACHE_LIFETIME_TICKS));
            REQUESTED.remove(request.key());
        }

        if ((clientTick & 31L) == 0L) {
            long oldestUsefulTick = clientTick - CACHE_LIFETIME_TICKS * 4L;
            ENVIRONMENT.entrySet().removeIf(entry -> entry.getValue().expiresAt() < oldestUsefulTick);
        }
    }

    static void clear() {
        playerSnapshot = NO_PLAYER;
        if (activeLevel == null && !sessionActive) {
            return;
        }
        startSession(null);
    }

    private static void startSession(ClientLevel level) {
        activeLevel = null;
        sessionActive = false;
        long nextSession = sessionGeneration + 1L;
        sessionGeneration = nextSession == 0L ? 1L : nextSession;
        ENVIRONMENT.clear();
        REQUESTED.clear();
        REQUESTS.clear();
        clientTick = 0L;
        if (level != null) {
            minimumBuildHeight = level.getMinBuildHeight();
            maximumBuildHeight = level.getMaxBuildHeight();
            activeLevel = level;
            sessionActive = true;
        } else {
            minimumBuildHeight = Integer.MAX_VALUE;
            maximumBuildHeight = Integer.MIN_VALUE;
        }
    }

    static PlayerSnapshot player() {
        return playerSnapshot;
    }

    static EnvironmentSample environmentAt(ClientLevel requesterLevel, double x, double y, double z) {
        long generation = sessionGeneration;
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);
        if (!sessionActive || requesterLevel != activeLevel
                || blockY < minimumBuildHeight || blockY >= maximumBuildHeight) {
            return EMPTY_ENVIRONMENT;
        }

        long blockPosition = BlockPos.asLong(blockX, blockY, blockZ);
        CacheEntry entry = ENVIRONMENT.get(blockPosition);
        boolean expired = entry == null || entry.expiresAt() <= clientTick;
        if (expired) {
            EnvironmentRequestKey key = new EnvironmentRequestKey(generation, blockPosition);
            if (REQUESTED.add(key) && !REQUESTS.offer(new EnvironmentRequest(key, blockX, blockY, blockZ))) {
                REQUESTED.remove(key);
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
        if (y < minimumBuildHeight || y >= maximumBuildHeight) {
            return 0.0D;
        }
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

    private record EnvironmentRequest(EnvironmentRequestKey key, int x, int y, int z) {
    }

    private record EnvironmentRequestKey(long sessionGeneration, long blockPosition) {
    }

    private record CacheEntry(EnvironmentSample sample, long expiresAt) {
    }
}
