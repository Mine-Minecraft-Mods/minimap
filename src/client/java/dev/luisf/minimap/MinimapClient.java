package dev.luisf.minimap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luisf.minimap.cache.MinimapCache;
import dev.luisf.minimap.config.MinimapConfig;
import dev.luisf.minimap.config.ConfigScreen;
import dev.luisf.minimap.hud.MinimapHud;
import dev.luisf.minimap.map.MinimapMap;
import dev.luisf.minimap.map.MinimapTexture;
import dev.luisf.minimap.screen.MinimapScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entry point: keys, events, chunk cache, textures and HUD registration. */
public final class MinimapClient implements ClientModInitializer {
	public static final String MOD_ID = "minimap";
	public static final Logger LOGGER = LoggerFactory.getLogger("Minimap");

	/** Ticks between minimap texture rebuilds (0.25 s). */
	private static final int TEXTURE_INTERVAL_TICKS = 5;
	/** Movement (in blocks) that triggers an immediate minimap rebuild. */
	private static final double TEXTURE_MOVE_THRESHOLD = 1.0;
	/** Ticks between marker (players, respawn) refreshes (0.5 s). */
	private static final int MARKER_INTERVAL_TICKS = 10;
	/** Client time spent scanning chunks per tick. */
	private static final long SCAN_BUDGET_NANOS = 2_000_000L;

	private static final int MINI_TEXTURE_SIZE = 256;
	private static final int MAP_TEXTURE_SIZE = 512;

	private static final Set<String> FAILED_HANDLERS = ConcurrentHashMap.newKeySet();

	private static MinimapConfig config;
	private static final MinimapCache CACHE = new MinimapCache();
	private static final MinimapMap MAP = new MinimapMap(CACHE);
	private static MinimapTexture miniTexture;
	private static MinimapTexture mapTexture;
	private static boolean texturesReady;

	private static KeyMapping openMapKey;
	private static KeyMapping configKey;

	private static ClientLevel currentLevel;
	private static Vec3 lastTextureCenter;
	private static int ticksSinceTexture = TEXTURE_INTERVAL_TICKS;
	private static int ticksSinceMarkers = MARKER_INTERVAL_TICKS;
	private static Markers markers = Markers.EMPTY;

	@Override
	public void onInitializeClient() {
		config = MinimapConfig.load(configPath());

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minimap.map", InputConstants.KEY_M, category));
		configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minimap.config", InputConstants.UNKNOWN.getValue(), category));

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> safely("chunk load", () -> {
			syncLevel(level);
			CACHE.onChunkLoad(chunk.getPos().pack());
		}));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> safely("chunk unload", () -> {
			if (level == currentLevel) {
				CACHE.onChunkUnload(chunk.getPos().pack());
			}
		}));
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> safely("level change", () -> syncLevel(level)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> safely("disconnect", MinimapClient::onDisconnect));
		ClientTickEvents.END_CLIENT_TICK.register(client -> safely("tick", () -> tick(client)));

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "minimap"), MinimapHud::extract);
	}

	public static MinimapConfig config() {
		return config;
	}

	public static MinimapCache cache() {
		return CACHE;
	}

	public static MinimapMap map() {
		return MAP;
	}

	public static MinimapTexture miniTexture() {
		return miniTexture;
	}

	public static MinimapTexture mapTexture() {
		return mapTexture;
	}

	/**
	 * Creates the GPU textures. Deferred to the first client tick because the render device is
	 * not ready yet during {@link #onInitializeClient()}.
	 */
	private static void ensureTextures() {
		if (texturesReady) {
			return;
		}
		miniTexture = new MinimapTexture("minimap", MINI_TEXTURE_SIZE, true);
		mapTexture = new MinimapTexture("minimap_map", MAP_TEXTURE_SIZE, false);
		texturesReady = true;
	}

	public static Markers markers() {
		return markers;
	}

	/** Replaces the config (from the config screen) and saves it. */
	public static void applyConfig(MinimapConfig newConfig) {
		config = newConfig;
		config.save(configPath());
	}

	/** Called by the mixin after the client changes a block. */
	public static void onBlockChanged(ClientLevel level, net.minecraft.core.BlockPos pos) {
		safely("block change", () -> {
			if (level == currentLevel) {
				CACHE.markDirty(pos);
			}
		});
	}

	/** Whether the minimap is allowed here: single-player, or multiplayer when configured. */
	public static boolean isAllowedHere(Minecraft client) {
		return client.hasSingleplayerServer() || config.allowMultiplayer;
	}

	/** Runs an event handler, logging its first failure instead of crashing the game. */
	public static void safely(String handler, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			if (FAILED_HANDLERS.add(handler)) {
				LOGGER.error("[Minimap] Error in {} handler; further errors there are not logged", handler, e);
			}
		}
	}

	private static void tick(Minecraft client) {
		ensureTextures();
		while (configKey.consumeClick()) {
			client.gui.setScreen(new ConfigScreen(client.gui.screen()));
		}
		while (openMapKey.consumeClick()) {
			client.gui.setScreen(new MinimapScreen(client.gui.screen()));
		}

		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			return;
		}
		syncLevel(level);

		boolean allowed = isAllowedHere(client);
		CACHE.setIndexing(allowed);
		if (allowed) {
			CACHE.scanPending(level, client, client.player.blockPosition().getX() >> 4,
					client.player.blockPosition().getZ() >> 4, SCAN_BUDGET_NANOS);
		}

		updateTexture(client, level, allowed);
		updateMarkers(client, level, allowed);
	}

	private static void updateTexture(Minecraft client, ClientLevel level, boolean allowed) {
		ticksSinceTexture++;
		Entity camera = client.getCameraEntity();
		if (camera == null) {
			return;
		}
		Vec3 center = camera.getPosition(1.0F);
		boolean due = allowed
				&& (ticksSinceTexture >= TEXTURE_INTERVAL_TICKS
						|| lastTextureCenter == null
						|| center.distanceToSqr(lastTextureCenter) > TEXTURE_MOVE_THRESHOLD * TEXTURE_MOVE_THRESHOLD);
		if (!due) {
			return;
		}
		double yaw = camera.getYRot();
		miniTexture.update(level, MAP, center.x, center.z, yaw, config.shape, config.miniRadius);
		lastTextureCenter = center;
		ticksSinceTexture = 0;
	}

	private static void updateMarkers(Minecraft client, ClientLevel level, boolean allowed) {
		ticksSinceMarkers++;
		if (ticksSinceMarkers < MARKER_INTERVAL_TICKS && markers != Markers.EMPTY) {
			return;
		}
		ticksSinceMarkers = 0;

		if (!allowed) {
			markers = Markers.EMPTY;
			return;
		}

		Vec3 respawn = null;
		LevelData.RespawnData respawnData = level.getRespawnData();
		if (respawnData != null && respawnData.dimension() == level.dimension()) {
			respawn = Vec3.atBottomCenterOf(respawnData.pos());
		}

		List<Marker> players = new ArrayList<>();
		if (config.showPlayers) {
			for (AbstractClientPlayer player : level.players()) {
				if (player == client.player || !player.isAlive()) {
					continue;
				}
				players.add(new Marker(player.position(), player.getScoreboardName()));
			}
		}
		markers = new Markers(respawn, List.copyOf(players));
	}

	/** Resets the cache and the texture state when the client changes world or disconnects. */
	private static void syncLevel(ClientLevel level) {
		if (level != currentLevel) {
			currentLevel = level;
			CACHE.clear();
			lastTextureCenter = null;
			ticksSinceTexture = TEXTURE_INTERVAL_TICKS;
			markers = Markers.EMPTY;
		}
	}

	private static void onDisconnect() {
		currentLevel = null;
		CACHE.clear();
		markers = Markers.EMPTY;
		lastTextureCenter = null;
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("minimap.json");
	}

	/** A point of interest drawn on the map. */
	public record Marker(Vec3 pos, String name) {}

	/** Immutable snapshot of the points of interest, read by the HUD and the expanded map. */
	public record Markers(Vec3 respawn, List<Marker> players) {
		public static final Markers EMPTY = new Markers(null, List.of());
	}
}
