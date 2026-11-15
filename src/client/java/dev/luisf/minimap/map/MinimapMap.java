package dev.luisf.minimap.map;

import dev.luisf.minimap.cache.MinimapCache;
import dev.luisf.minimap.cache.MinimapCache.ChunkData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Reads the top-down colour of the world from the chunk cache. Pure lookups: all the heavy
 * work (finding the topmost block) happens in {@link MinimapCache}.
 */
public final class MinimapMap {
	private final MinimapCache cache;

	public MinimapMap(MinimapCache cache) {
		this.cache = cache;
	}

	/**
	 * Colour of the column at {@code (x, z)} as ARGB, shaded by height, or {@code -1} when the
	 * chunk is not loaded or not scanned yet.
	 */
	public int colorAt(ClientLevel level, int x, int z) {
		ChunkData data = cache.data(ChunkPos.pack(x >> 4, z >> 4));
		if (data == null || !data.scanned) {
			return -1;
		}
		int index = (z & 15) * 16 + (x & 15);
		int argb = data.colors[index];
		if (argb == 0) {
			return -1;
		}
		float shade = MinimapMath.heightShade(data.heights[index], level.getSeaLevel());
		return MinimapMath.scaleRgb(argb, shade);
	}
}
