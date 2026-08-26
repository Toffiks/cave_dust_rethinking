package net.lizistired.cavedust;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Loads, validates and atomically saves the client-side Cave Dust settings. */
final class CaveDustConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaveDustMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_PARTICLE_ID = "cavedust:cave_dust_mote";

    private final Path path;
    private int width = 10;
    private boolean caveDustEnabled = true;
    private boolean seaLevelCheck = true;
    private boolean superFlatStatus = false;
    private float upperLimit = 64.0F;
    private float lowerLimit = -64.0F;
    private int particleMultiplier = 14;
    private int listNumber = 0;
    private String newId = DEFAULT_PARTICLE_ID;
    private transient ParticleOptions selectedParticle;
    private transient boolean dirty;

    private CaveDustConfig(Path path) {
        this.path = path;
    }

    static CaveDustConfig load(Path path) {
        CaveDustConfig config = new CaveDustConfig(path);
        config.reload();
        return config;
    }

    void reload() {
        boolean readable = Files.isReadable(path);
        boolean needsSave = !readable;
        applyDefaults();
        if (readable) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject values = JsonParser.parseReader(reader).getAsJsonObject();
                width = getInt(values, "width", width);
                caveDustEnabled = getBoolean(values, "caveDustEnabled", caveDustEnabled);
                seaLevelCheck = getBoolean(values, "seaLevelCheck", seaLevelCheck);
                superFlatStatus = getBoolean(values, "superFlatStatus", superFlatStatus);
                upperLimit = getFloat(values, "upperLimit", upperLimit);
                lowerLimit = getFloat(values, "lowerLimit", lowerLimit);
                particleMultiplier = getInt(values, "particleMultiplier", particleMultiplier);
                newId = getString(values, "newId", getString(values, "particle", newId));
                needsSave = values.has("height")
                        || values.has("velocityRandomness")
                        || values.has("particleMultiplierMultiplier")
                        || values.has("particle");
            } catch (Exception exception) {
                LOGGER.warn("Invalid Cave Dust configuration; using safe values", exception);
                needsSave = true;
            }
        }
        selectedParticle = null;
        boolean sanitized = sanitize();
        dirty = needsSave || sanitized;
        saveIfDirty();
    }

    void saveIfDirty() {
        if (!dirty) {
            return;
        }

        JsonObject values = new JsonObject();
        values.addProperty("width", width);
        values.addProperty("caveDustEnabled", caveDustEnabled);
        values.addProperty("seaLevelCheck", seaLevelCheck);
        values.addProperty("superFlatStatus", superFlatStatus);
        values.addProperty("upperLimit", upperLimit);
        values.addProperty("lowerLimit", lowerLimit);
        values.addProperty("particleMultiplier", particleMultiplier);
        values.addProperty("newId", newId);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(
                    temporary,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                GSON.toJson(values, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException exception) {
            LOGGER.warn("Could not save Cave Dust configuration to {}", path, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The next successful save will replace the temporary file.
            }
        }
    }

    boolean toggleEnabled() {
        caveDustEnabled = !caveDustEnabled;
        dirty = true;
        return caveDustEnabled;
    }

    boolean enabled() { return caveDustEnabled; }
    boolean seaLevelCheck() { return seaLevelCheck; }
    boolean superflatEnabled() { return superFlatStatus; }
    int width() { return width; }
    float upperLimit() { return upperLimit; }
    float lowerLimit() { return lowerLimit; }
    int particleMultiplier() { return particleMultiplier; }

    void setParticleMultiplier(int value) {
        int safeValue = clamp(value, 1, 100);
        if (particleMultiplier != safeValue) {
            particleMultiplier = safeValue;
            dirty = true;
        }
    }

    void setWidth(int value) {
        int safeValue = clamp(value, 1, 50);
        if (width != safeValue) {
            width = safeValue;
            dirty = true;
        }
    }

    ParticleOptions particle() {
        if (selectedParticle != null) {
            return selectedParticle;
        }

        ParticleOptions options = resolveParticle(newId);
        if (options != null) {
            selectedParticle = options;
            return selectedParticle;
        }

        LOGGER.warn("Unknown or parameterized particle '{}'; falling back to {}", newId, DEFAULT_PARTICLE_ID);
        selectDefaultParticle();
        dirty = true;
        options = resolveParticle(newId);
        if (options == null) {
            throw new IllegalStateException("Cave Dust particle type is not registered: " + newId);
        }
        selectedParticle = options;
        saveIfDirty();
        return selectedParticle;
    }

    String particleName() {
        return newId;
    }

    void iterateParticle() {
        List<ResourceLocation> particleIds = particleIds();
        for (int attempts = 0; attempts < particleIds.size(); attempts++) {
            listNumber = (listNumber + 1) % particleIds.size();
            String candidate = particleIds.get(listNumber).toString();
            ParticleOptions options = resolveParticle(candidate);
            if (options != null) {
                newId = candidate;
                selectedParticle = options;
                dirty = true;
                return;
            }
        }

        selectDefaultParticle();
        dirty = true;
    }

    void reset() {
        applyDefaults();
        dirty = true;
    }

    private void applyDefaults() {
        width = 10;
        caveDustEnabled = true;
        seaLevelCheck = true;
        superFlatStatus = false;
        upperLimit = 64.0F;
        lowerLimit = -64.0F;
        particleMultiplier = 14;
        listNumber = 0;
        newId = DEFAULT_PARTICLE_ID;
        selectedParticle = null;
    }

    private boolean sanitize() {
        int previousWidth = width;
        int previousMultiplier = particleMultiplier;
        float previousLowerLimit = lowerLimit;
        float previousUpperLimit = upperLimit;
        int previousListNumber = listNumber;
        String previousId = newId;

        width = clamp(width, 1, 50);
        particleMultiplier = clamp(particleMultiplier, 1, 100);
        if (!Float.isFinite(lowerLimit) || !Float.isFinite(upperLimit) || upperLimit <= lowerLimit) {
            lowerLimit = -64.0F;
            upperLimit = 64.0F;
        }

        List<ResourceLocation> particleIds = particleIds();
        if (particleIds.isEmpty()) {
            throw new IllegalStateException("The particle registry is empty");
        }
        int configuredIndex = indexOfParticle(newId, particleIds);
        if (configuredIndex >= 0) {
            listNumber = configuredIndex;
        } else {
            selectDefaultParticle();
        }
        return width != previousWidth
                || particleMultiplier != previousMultiplier
                || Float.compare(lowerLimit, previousLowerLimit) != 0
                || Float.compare(upperLimit, previousUpperLimit) != 0
                || listNumber != previousListNumber
                || !java.util.Objects.equals(newId, previousId);
    }

    private ParticleOptions resolveParticle(String id) {
        try {
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(id));
            return type instanceof ParticleOptions options ? options : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private List<ResourceLocation> particleIds() {
        return List.copyOf(BuiltInRegistries.PARTICLE_TYPE.keySet());
    }

    private int indexOfParticle(String id, List<ResourceLocation> particleIds) {
        if (id != null) {
            for (int index = 0; index < particleIds.size(); index++) {
                if (particleIds.get(index).toString().equals(id)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private void selectDefaultParticle() {
        newId = DEFAULT_PARTICLE_ID;
        selectedParticle = null;
        int defaultIndex = indexOfParticle(DEFAULT_PARTICLE_ID, particleIds());
        listNumber = Math.max(defaultIndex, 0);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int getInt(JsonObject values, String key, int fallback) {
        return values.has(key) && values.get(key).isJsonPrimitive() ? values.get(key).getAsInt() : fallback;
    }

    private static float getFloat(JsonObject values, String key, float fallback) {
        return values.has(key) && values.get(key).isJsonPrimitive() ? values.get(key).getAsFloat() : fallback;
    }

    private static boolean getBoolean(JsonObject values, String key, boolean fallback) {
        return values.has(key) && values.get(key).isJsonPrimitive() ? values.get(key).getAsBoolean() : fallback;
    }

    private static String getString(JsonObject values, String key, String fallback) {
        return values.has(key) && values.get(key).isJsonPrimitive() ? values.get(key).getAsString() : fallback;
    }
}
