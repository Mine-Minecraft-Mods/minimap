package dev.luisf.minimap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinimapConfigTest {
	@Test
	void defaultsAreValid() {
		MinimapConfig config = MinimapConfig.defaults();
		assertEquals(MinimapConfig.DEFAULT_MINI_RADIUS, config.miniRadius);
		assertEquals(MinimapConfig.DEFAULT_MINI_SIZE, config.miniSize);
		assertEquals(MinimapConfig.DEFAULT_MAP_RADIUS, config.mapRadius);
		assertTrue(config.rotate);
		assertEquals(MinimapConfig.Shape.SQUARE, config.shape);
	}

	@Test
	void clampsOutOfRangeValues() {
		MinimapConfig config = new MinimapConfig();
		config.miniRadius = 1;
		config.miniSize = 9999;
		config.mapRadius = 0;
		config.shape = null;
		config.normalize();
		assertEquals(MinimapConfig.MIN_MINI_RADIUS, config.miniRadius);
		assertEquals(MinimapConfig.MAX_MINI_SIZE, config.miniSize);
		assertEquals(MinimapConfig.MIN_MAP_RADIUS, config.mapRadius);
		assertEquals(MinimapConfig.Shape.SQUARE, config.shape);
	}

	@Test
	void roundTripsThroughJson(@TempDir Path dir) {
		Path file = dir.resolve("minimap.json");
		MinimapConfig config = MinimapConfig.defaults();
		config.miniRadius = 48;
		config.rotate = false;
		config.shape = MinimapConfig.Shape.CIRCLE;
		config.save(file);

		MinimapConfig loaded = MinimapConfig.load(file);
		assertEquals(48, loaded.miniRadius);
		assertEquals(false, loaded.rotate);
		assertEquals(MinimapConfig.Shape.CIRCLE, loaded.shape);
	}

	@Test
	void createsMissingFileWithDefaults(@TempDir Path dir) {
		Path file = dir.resolve("minimap.json");
		MinimapConfig loaded = MinimapConfig.load(file);
		assertEquals(MinimapConfig.defaults().miniRadius, loaded.miniRadius);
		assertTrue(Files.exists(file));
	}

	@Test
	void backsUpBrokenFileAndRestoresDefaults(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("minimap.json");
		Files.writeString(file, "{ not json");
		MinimapConfig loaded = MinimapConfig.load(file);
		assertEquals(MinimapConfig.defaults().miniRadius, loaded.miniRadius);
		assertTrue(Files.exists(dir.resolve("minimap.json.bak")));
	}
}
