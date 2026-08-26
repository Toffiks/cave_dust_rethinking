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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes main-thread snapshots for particles and shares block-environment
 * checks between particles occupying the same block.
 */
final class CaveDustParticleContext {
    private static final int CACHE_LIFETIME_TICKS = 5;
    private static final int MAX_REQUESTS_PER_TICK = 2048;
    private static final int MAX_QUEUED_REQUESTS = 4096;
    private static final EnvironmentSample EMPTY_ENVIRONMENT = new EnvironmentSample(false, false, 0.0D);
    private static final PlayerSnapshot NO_PLAYER = new PlayerSnapshot(
            false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    private static final Map<Long, CacheEntry> ENVIRONMENT = new ConcurrentHashMap<>();
    private static final Map<Long, Long> REQUESTED = new ConcurrentHashMap<>();
    private static final Queue<EnvironmentRequest> REQUESTS = new ArrayBlockingQueue<>(MAX_QUEUED_REQUESTS);

    private static volatile PlayerSnapshot playerSnapshot = NO_PLAYER;
    private static volatile long clientTick;
    private static volatile long activeSession;
    private static volatile ClientLevel activeLevel;
    private static volatile int minimumBuildHeight = Integer.MAX_VALUE;
    private static volatile int maximumBuildHeight = Integer.MIN_VALUE;

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
            if (request.session() != activeSession) {
                REQUESTED.remove(request.key(), request.session());
                continue;
            }
            EnvironmentSample sample = sampleEnvironment(level, cursor, request.x(), request.y(), request.z());
            ENVIRONMENT.put(request.key(), new CacheEntry(sample, activeSession, clientTick + CACHE_LIFETIME_TICKS));
            REQUESTED.remove(request.key(), request.session());
        }

        if ((clientTick & 31L) == 0L) {
            long oldestUsefulTick = clientTick - CACHE_LIFETIME_TICKS * 4L;
            ENVIRONMENT.entrySet().removeIf(entry -> entry.getValue().session() != activeSession
                    || entry.getValue().expiresAt() < oldestUsefulTick);
        }
    }

    static void clear() {
        if (activeLevel == null) {
            playerSnapshot = NO_PLAYER;
            return;
        }
        startSession(null);
    }

    private static void startSession(ClientLevel level) {
        // Publish the inactive state first so an old async particle cannot join the next session.
        activeLevel = null;
        long nextSession = activeSession + 1L;
        activeSession = nextSession == 0L ? 1L : nextSession;
        playerSnapshot = NO_PLAYER;
        clientTick = 0L;
        ENVIRONMENT.clear();
        REQUESTED.clear();
        REQUESTS.clear();
        if (level != null) {
            minimumBuildHeight = level.getMinY();
            maximumBuildHeight = level.getMaxY();
        } else {
            minimumBuildHeight = Integer.MAX_VALUE;
            maximumBuildHeight = Integer.MIN_VALUE;
        }
        activeLevel = level;
    }

    static PlayerSnapshot player() {
        return playerSnapshot;
    }

    static EnvironmentSample environmentAt(ClientLevel requesterLevel, double x, double y, double z) {
        long session = activeSession;
        if (session == 0L || requesterLevel != activeLevel) {
            return EMPTY_ENVIRONMENT;
        }

        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);
        if (blockY < minimumBuildHeight || blockY >= maximumBuildHeight) {
            return EMPTY_ENVIRONMENT;
        }
        long key = BlockPos.asLong(blockX, blockY, blockZ);
        CacheEntry entry = ENVIRONMENT.get(key);
        boolean expired = entry == null || entry.session() != session || entry.expiresAt() <= clientTick;
        if (expired) {
            requestEnvironment(key, session, blockX, blockY, blockZ);
        }
        return expired ? EMPTY_ENVIRONMENT : entry.sample();
    }

    private static void requestEnvironment(long key, long session, int x, int y, int z) {
        Long requestedSession = REQUESTED.get(key);
        if (requestedSession != null && requestedSession == session) {
            return;
        }

        boolean registered = requestedSession == null
                ? REQUESTED.putIfAbsent(key, session) == null
                : REQUESTED.replace(key, requestedSession, session);
        if (!registered) {
            return;
        }

        if (!REQUESTS.offer(new EnvironmentRequest(session, key, x, y, z))) {
            REQUESTED.remove(key, session);
        }
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

    private record EnvironmentRequest(long session, long key, int x, int y, int z) {
    }

    private record CacheEntry(EnvironmentSample sample, long session, long expiresAt) {
    }
}
