package dev.luisf.minimap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent options, stored as JSON in {@code config/minimap.json}. No Minecraft
 * dependencies, so it can be unit-tested.
 */
public final class MinimapConfig {
	public static final int CURRENT_VERSION = 1;

	public static final int MIN_MINI_RADIUS = 8;
	public static final int MAX_MINI_RADIUS = 128;
	public static final int DEFAULT_MINI_RADIUS = 32;

	public static final int MIN_MINI_SIZE = 48;
	public static final int MAX_MINI_SIZE = 256;
	public static final int DEFAULT_MINI_SIZE = 128;

	public static final int MIN_MAP_RADIUS = 32;
	public static final int MAX_MAP_RADIUS = 256;
	public static final int DEFAULT_MAP_RADIUS = 128;

	/** Shape of the minimap in the corner. */
	public enum Shape { SQUARE, CIRCLE }

	public int version = CURRENT_VERSION;
	/** Blocks from the player to each edge of the corner minimap. */
	public int miniRadius = DEFAULT_MINI_RADIUS;
	/** Screen size of the corner minimap, in pixels. */
	public int miniSize = DEFAULT_MINI_SIZE;
	/** Rotate the corner minimap with the camera; when false, north is always up. */
	public boolean rotate = true;
	public Shape shape = Shape.SQUARE;
	/** Blocks from the map centre to each edge of the expanded map texture. */
	public int mapRadius = DEFAULT_MAP_RADIUS;

	public boolean showCoordinates = true;
	public boolean showCompass = true;
	public boolean showPlayers = true;
	public boolean showRespawn = true;
	public boolean allowMultiplayer = false;

	public static MinimapConfig defaults() {
		MinimapConfig config = new MinimapConfig();
		config.normalize();
		return config;
	}

	/**
	 * Loads the config, creating it with defaults when missing. A file that cannot be parsed
	 * is renamed to {@code <name>.bak} and replaced by the defaults.
	 */
	public static MinimapConfig load(Path file) {
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				MinimapConfig config = GSON.fromJson(reader, MinimapConfig.class);
				if (config == null) {
					throw new JsonParseException("empty file");
				}
				config.normalize();
				return config;
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("[Minimap] Could not read {} ({}); backing it up and restoring defaults", file, e.getMessage());
				backUp(file);
			}
		}

		MinimapConfig config = defaults();
		config.save(file);
		return config;
	}

	public void save(Path file) {
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("[Minimap] Could not save {}: {}", file, e.getMessage());
		}
	}

	public MinimapConfig copy() {
		MinimapConfig copy = GSON.fromJson(GSON.toJson(this), MinimapConfig.class);
		copy.normalize();
		return copy;
	}

	/** Clamps values to their valid ranges and fills in anything missing. */
	public void normalize() {
		version = CURRENT_VERSION;
		miniRadius = Math.clamp(miniRadius, MIN_MINI_RADIUS, MAX_MINI_RADIUS);
		miniSize = Math.clamp(miniSize, MIN_MINI_SIZE, MAX_MINI_SIZE);
		mapRadius = Math.clamp(mapRadius, MIN_MAP_RADIUS, MAX_MAP_RADIUS);
		if (shape == null) {
			shape = Shape.SQUARE;
		}
	}

	private static void backUp(Path file) {
		try {
			Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn("[Minimap] Could not back up {}: {}", file, e.getMessage());
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("Minimap");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
}
