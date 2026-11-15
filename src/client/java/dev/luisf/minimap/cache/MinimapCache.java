package dev.luisf.minimap.cache;

import dev.luisf.minimap.MinimapClient;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.MapColor;

/**
 * Topmost block colour and height of every column of each loaded chunk, keyed by
 * {@link ChunkPos#pack()}, plus the queue of chunks waiting to be scanned. Client thread only.
 *
 * <p>The scan is incremental: a chunk is read one column at a time within a small time budget
 * per tick, so opening the world never stalls the client.
 */
public final class MinimapCache {
	/** Colours and heights of one chunk: index {@code (z & 15) * 16 + (x & 15)}. */
	public static final class ChunkData {
		public final int[] colors = new int[256];
		public final int[] heights = new int[256];
		public boolean scanned;
	}

	private final Long2ObjectOpenHashMap<ChunkData> chunks = new Long2ObjectOpenHashMap<>();
	/** Every chunk the client has loaded, tracked even while indexing is off. */
	private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
	private final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
	private boolean indexing = true;
	private long version;

	public void onChunkLoad(long chunkKey) {
		loadedChunks.add(chunkKey);
		if (indexing) {
			// A chunk can be resent: drop the old data and scan again.
			if (chunks.remove(chunkKey) != null) {
				version++;
			}
			pending.add(chunkKey);
		}
	}

	public void onChunkUnload(long chunkKey) {
		loadedChunks.remove(chunkKey);
		pending.remove(chunkKey);
		if (chunks.remove(chunkKey) != null) {
			version++;
		}
	}

	/** Forgets everything, including which chunks are loaded (world change, disconnect). */
	public void clear() {
		loadedChunks.clear();
		dropData();
	}

	/** Marks a loaded chunk for a new scan (a block changed). */
	public void markDirty(BlockPos pos) {
		long key = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
		if (loadedChunks.contains(key) && chunks.containsKey(key)) {
			pending.add(key);
		}
	}

	public boolean hasPending() {
		return !pending.isEmpty();
	}

	/** Removes and returns the pending chunk closest to the given chunk, or {@code null}. */
	public Long pollNearest(int chunkX, int chunkZ) {
		long best = 0;
		long bestDistance = Long.MAX_VALUE;
		for (LongIterator it = pending.iterator(); it.hasNext(); ) {
			long key = it.nextLong();
			long dx = ChunkPos.getX(key) - chunkX;
			long dz = ChunkPos.getZ(key) - chunkZ;
			long distance = dx * dx + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = key;
			}
		}
		if (bestDistance == Long.MAX_VALUE) {
			return null;
		}
		pending.remove(best);
		return best;
	}

	/** The scanned data of a chunk, or {@code null} if it is not loaded or not scanned yet. */
	public ChunkData data(long chunkKey) {
		return chunks.get(chunkKey);
	}

	/** Starts a fresh (empty) entry for a chunk about to be scanned and returns it. */
	public ChunkData beginChunk(long chunkKey) {
		ChunkData data = new ChunkData();
		if (chunks.put(chunkKey, data) != null) {
			version++;
		}
		return data;
	}

	/** Increases whenever the scanned colours change. */
	public long version() {
		return version;
	}

	public boolean isIndexing() {
		return indexing;
	}

	public void setIndexing(boolean indexing) {
		this.indexing = indexing;
		if (!indexing) {
			dropData();
		}
	}

	/**
	 * Reads the topmost non-air block of every column of a chunk. Called on the client thread
	 * with the chunk already loaded.
	 */
	public void scanChunk(ClientLevel level, Minecraft client, long chunkKey) {
		ChunkData data = beginChunk(chunkKey);
		LevelChunk chunk = level.getChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
		if (chunk == null) {
			data.scanned = true;
			return;
		}

		LevelChunkSection[] sections = chunk.getSections();
		int minY = chunk.getMinY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int localZ = 0; localZ < 16; localZ++) {
			for (int localX = 0; localX < 16; localX++) {
				int topY = minY;
				net.minecraft.world.level.block.state.BlockState top = null;
				for (int section = sections.length - 1; section >= 0 && top == null; section--) {
					LevelChunkSection chunkSection = sections[section];
					if (chunkSection.hasOnlyAir()) {
						continue;
					}
					int baseY = minY + (section << 4);
					for (int localY = 15; localY >= 0; localY--) {
						net.minecraft.world.level.block.state.BlockState state = chunkSection.getBlockState(localX, localY, localZ);
						if (!state.isAir()) {
							top = state;
							topY = baseY + localY;
							break;
						}
					}
				}
				if (top == null) {
					continue;
				}

				int worldX = (ChunkPos.getX(chunkKey) << 4) + localX;
				int worldZ = (ChunkPos.getZ(chunkKey) << 4) + localZ;
				pos.set(worldX, topY, worldZ);
				int index = localZ * 16 + localX;
				data.colors[index] = colorOf(level, client, top, pos);
				data.heights[index] = topY;
			}
		}
		data.scanned = true;
		version++;
	}

	/** Colour of the top face of a block: biome tint when the block has one, map colour otherwise. */
	private static int colorOf(ClientLevel level, Minecraft client,
			net.minecraft.world.level.block.state.BlockState state, BlockPos.MutableBlockPos pos) {
		if (state.getFluidState().is(FluidTags.WATER)) {
			return ARGB.opaque(BiomeColors.getAverageWaterColor(level, pos));
		}

		MapColor mapColor = state.getMapColor(level, pos);
		int argb = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
		BlockTintSource tint = client.getBlockColors().getTintSource(state, 0);
		if (tint != null) {
			int tinted = tint.colorInWorld(state, level, pos);
			if (tinted != -1 && (tinted & 0xFF000000) != 0) {
				return tinted;
			}
		}
		return argb;
	}

	private void dropData() {
		if (!chunks.isEmpty()) {
			version++;
		}
		chunks.clear();
		pending.clear();
	}

	/** Called by the client tick to scan pending chunks within a time budget. */
	public void scanPending(ClientLevel level, Minecraft client, int chunkX, int chunkZ, long budgetNanos) {
		long deadline = System.nanoTime() + budgetNanos;
		while (hasPending() && System.nanoTime() < deadline) {
			Long key = pollNearest(chunkX, chunkZ);
			if (key == null) {
				return;
			}
			MinimapClient.safely("chunk scan", () -> scanChunk(level, client, key));
		}
	}
}
