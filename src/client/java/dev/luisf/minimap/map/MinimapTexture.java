package dev.luisf.minimap.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlSampler;
import dev.luisf.minimap.MinimapClient;
import dev.luisf.minimap.config.MinimapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.OptionalDouble;

/**
 * A square texture holding the top-down map. The texture is centred on a world position and
 * covers {@code 2 * radius} blocks; each pixel samples one block from {@link MinimapMap}.
 *
 * <p>The corner minimap uses a small texture (256 px) rebuilt a few times per second. The
 * expanded map uses a large one (512 px) rebuilt in slices, so it fills in gradually instead
 * of freezing the client.
 *
 * <p>In Minecraft 26.x the {@code blit(Identifier, ...)} path looks up textures through the
 * resource manager and never finds textures registered manually in {@code TextureManager},
 * so we expose the {@link GpuTextureView} and a {@link GpuSampler} and let callers use the
 * {@code blit(GpuTextureView, GpuSampler, ...)} path instead.
 */
public final class MinimapTexture {
	private static final GpuSampler SAMPLER = new GlSampler(
			AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
			FilterMode.NEAREST, FilterMode.NEAREST,
			1, OptionalDouble.empty());

	private final int size;
	private final boolean rotate;
	private final Identifier id;
	private final DynamicTexture texture;
	private int slice;

	public MinimapTexture(String name, int size, boolean rotate) {
		this.size = size;
		this.rotate = rotate;
		this.id = Identifier.fromNamespaceAndPath(MinimapClient.MOD_ID, name);
		this.texture = new DynamicTexture("minimap/" + name, size, size, false);
		Minecraft.getInstance().getTextureManager().register(id, texture);
	}

	public Identifier id() {
		return id;
	}

	public int size() {
		return size;
	}

	/** The GPU texture view, for rendering with {@code blit(GpuTextureView, GpuSampler, ...)}. */
	public GpuTextureView view() {
		return texture.getTextureView();
	}

	/** Nearest-neighbor sampler that clamps to the edge. */
	public GpuSampler sampler() {
		return SAMPLER;
	}

	/** Rebuilds every pixel around {@code (centerX, centerZ)}. */
	public void update(ClientLevel level, MinimapMap map, double centerX, double centerZ, double yaw,
			MinimapConfig.Shape shape, int radius) {
		for (int row = 0; row < size; row++) {
			updateRow(level, map, centerX, centerZ, yaw, shape, radius, row);
		}
		slice = 0;
		texture.upload();
	}

	/** Rebuilds {@code rows} pixel rows, continuing where the last call stopped. */
	public void updateSlice(ClientLevel level, MinimapMap map, double centerX, double centerZ, double yaw,
			MinimapConfig.Shape shape, int radius, int rows) {
		for (int i = 0; i < rows; i++) {
			updateRow(level, map, centerX, centerZ, yaw, shape, radius, slice);
			slice = (slice + 1) % size;
		}
		texture.upload();
	}

	private void updateRow(ClientLevel level, MinimapMap map, double centerX, double centerZ, double yaw,
			MinimapConfig.Shape shape, int radius, int row) {
		NativeImage pixels = texture.getPixels();
		double screenY = (row + 0.5) / size * 2.0 - 1.0;
		boolean circle = shape == MinimapConfig.Shape.CIRCLE;

		for (int x = 0; x < size; x++) {
			double screenX = (x + 0.5) / size * 2.0 - 1.0;
			int color = MinimapMap.UNLOADED_COLOR;
			if (!circle || screenX * screenX + screenY * screenY <= 1.0) {
				double[] offset = MinimapMath.worldOffset(screenX * radius, screenY * radius, yaw, rotate);
				int blockX = (int) Math.floor(centerX + offset[0]);
				int blockZ = (int) Math.floor(centerZ + offset[1]);
				int sampled = map.colorAt(level, blockX, blockZ);
				color = sampled;
			}
			pixels.setPixel(x, row, color);
		}
	}

	/** Closes the GPU texture and sampler (world change). */
	public void close() {
		texture.close();
	}
}
