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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class CaveDustConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaveDustMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_PARTICLE_ID = "cavedust:cave_dust_mote";

    private final Path path;
    private int width = 10;
    private int height = 10;
    private int velocityRandomness = 0;
    private boolean caveDustEnabled = true;
    private boolean seaLevelCheck = true;
    private boolean superFlatStatus = false;
    private float upperLimit = 64.0F;
    private float lowerLimit = -64.0F;
    private int particleMultiplier = 14;
    private int listNumber = 0;
    private int particleMultiplierMultiplier = 10;
    private String newId = DEFAULT_PARTICLE_ID;

    private CaveDustConfig(Path path) {
        this.path = path;
    }

    static CaveDustConfig load(Path path) {
        CaveDustConfig config = new CaveDustConfig(path);
        config.reload();
        return config;
    }

    void reload() {
        if (Files.isReadable(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject values = JsonParser.parseReader(reader).getAsJsonObject();
                width = getInt(values, "width", width);
                height = getInt(values, "height", height);
                velocityRandomness = getInt(values, "velocityRandomness", velocityRandomness);
                caveDustEnabled = getBoolean(values, "caveDustEnabled", caveDustEnabled);
                seaLevelCheck = getBoolean(values, "seaLevelCheck", seaLevelCheck);
                superFlatStatus = getBoolean(values, "superFlatStatus", superFlatStatus);
                upperLimit = getFloat(values, "upperLimit", upperLimit);
                lowerLimit = getFloat(values, "lowerLimit", lowerLimit);
                particleMultiplier = getInt(values, "particleMultiplier", particleMultiplier);
                listNumber = getInt(values, "listNumber", listNumber);
                particleMultiplierMultiplier = getInt(values, "particleMultiplierMultiplier", particleMultiplierMultiplier);
                newId = getString(values, "newId", getString(values, "particle", newId));
            } catch (Exception exception) {
                LOGGER.warn("Invalid Cave Dust configuration; using safe values", exception);
            }
        }
        sanitize();
        save();
    }

    private void save() {
        JsonObject values = new JsonObject();
        values.addProperty("width", width);
        values.addProperty("height", height);
        values.addProperty("velocityRandomness", velocityRandomness);
        values.addProperty("caveDustEnabled", caveDustEnabled);
        values.addProperty("seaLevelCheck", seaLevelCheck);
        values.addProperty("superFlatStatus", superFlatStatus);
        values.addProperty("upperLimit", upperLimit);
        values.addProperty("lowerLimit", lowerLimit);
        values.addProperty("particleMultiplier", particleMultiplier);
        values.addProperty("listNumber", listNumber);
        values.addProperty("particleMultiplierMultiplier", particleMultiplierMultiplier);
        values.addProperty("newId", newId);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(values, writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save Cave Dust configuration to {}", path, exception);
        }
    }

    boolean toggleEnabled() {
        caveDustEnabled = !caveDustEnabled;
        save();
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
            save();
        }
    }

    void setWidth(int value) {
        int safeValue = clamp(value, 1, 50);
        if (width != safeValue) {
            width = safeValue;
            save();
        }
    }

    ParticleOptions particle() {
        ParticleOptions options = resolveParticle(newId);
        if (options != null) {
            return options;
        }

        LOGGER.warn("Unknown or parameterized particle '{}'; falling back to {}", newId, DEFAULT_PARTICLE_ID);
        selectDefaultParticle();
        save();
        options = resolveParticle(newId);
        if (options == null) {
            throw new IllegalStateException("Cave Dust particle type is not registered: " + newId);
        }
        return options;
    }

    String particleName() {
        particle();
        return newId;
    }

    void iterateParticle() {
        List<ResourceLocation> particleIds = particleIds();
        for (int attempts = 0; attempts < particleIds.size(); attempts++) {
            listNumber = (listNumber + 1) % particleIds.size();
            String candidate = particleIds.get(listNumber).toString();
            if (resolveParticle(candidate) != null) {
                newId = candidate;
                save();
                return;
            }
        }

        selectDefaultParticle();
        save();
    }

    void reset() {
        width = 10;
        height = 10;
        velocityRandomness = 0;
        caveDustEnabled = true;
        seaLevelCheck = true;
        superFlatStatus = false;
        upperLimit = 64.0F;
        lowerLimit = -64.0F;
        particleMultiplier = 14;
        listNumber = 0;
        particleMultiplierMultiplier = 10;
        newId = DEFAULT_PARTICLE_ID;
        save();
    }

    private void sanitize() {
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
