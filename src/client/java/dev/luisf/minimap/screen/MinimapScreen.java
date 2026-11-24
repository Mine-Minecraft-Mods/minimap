package dev.luisf.minimap.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luisf.minimap.MinimapClient;
import dev.luisf.minimap.MinimapClient.Marker;
import dev.luisf.minimap.config.MinimapConfig;
import dev.luisf.minimap.map.MinimapMath;
import dev.luisf.minimap.map.MinimapTexture;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Full-screen map opened with {@code M}. The map texture is centred on a world position and
 * rebuilt in slices; dragging pans it and the scroll wheel zooms. {@code M} or {@code Esc}
 * closes it.
 */
public final class MinimapScreen extends Screen {
	private static final double MIN_BLOCKS_PER_PIXEL = 0.25;
	private static final double MAX_BLOCKS_PER_PIXEL = 32.0;
	private static final int SLICE_ROWS = 128;
	private static final int BACKGROUND = 0xCC000000;
	private static final int PANEL = 0x88000000;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int PLAYER_COLOR = 0xFFFFFFFF;
	private static final int RESPAWN_COLOR = 0xFFFFD83D;
	private static final int OTHER_PLAYER_COLOR = 0xFF4FC3F7;

	private final Screen parent;
	private double centerX;
	private double centerZ;
	private double blocksPerPixel = 2.0;
	private double offsetX;
	private double offsetY;
	private boolean dragging;

	public MinimapScreen(Screen parent) {
		super(Component.translatable("minimap.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Minecraft client = Minecraft.getInstance();
		Vec3 position = client.player == null ? Vec3.ZERO : client.player.position();
		centerOn(position.x, position.z);
	}

	@Override
	public void tick() {
		MinimapClient.safely("map tick", () -> {
			Minecraft client = Minecraft.getInstance();
			if (client.level == null || MinimapClient.mapTexture() == null) {
				return;
			}
			MinimapClient.mapTexture().updateSlice(client.level, MinimapClient.map(), centerX, centerZ, 0.0,
					MinimapConfig.Shape.SQUARE, MinimapClient.config().mapRadius, SLICE_ROWS);
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(0, 0, width, height, BACKGROUND);

		Minecraft client = Minecraft.getInstance();
		if (MinimapClient.mapTexture() == null) {
			return;
		}
		MinimapConfig config = MinimapClient.config();
		double drawSize = config.mapRadius * 2.0 / blocksPerPixel;
		double drawX = width / 2.0 + offsetX - drawSize / 2.0;
		double drawY = height / 2.0 + offsetY - drawSize / 2.0;

		MinimapTexture mapTexture = MinimapClient.mapTexture();
		graphics.blit(mapTexture.view(), mapTexture.sampler(), (int) Math.round(drawX), (int) Math.round(drawY),
				(int) Math.ceil(drawSize), (int) Math.ceil(drawSize), 0.0F, 0.0F, 1.0F, 1.0F);

		if (client.player != null) {
			drawMarkers(graphics, client);
			drawPlayer(graphics, client, partialTick);
		}
		drawOverlay(graphics, client, mouseX, mouseY);
	}

	private void drawOverlay(GuiGraphicsExtractor graphics, Minecraft client, int mouseX, int mouseY) {
		Component title = Component.translatable("minimap.screen.title");
		graphics.text(client.font, title, 6, 5, TEXT_COLOR, true);

		if (client.player != null) {
			int worldX = (int) Math.floor(screenToWorldX(mouseX));
			int worldZ = (int) Math.floor(screenToWorldY(mouseY));
			Component coordinates = Component.translatable("minimap.screen.coordinates", worldX, worldZ);
			int width = client.font.width(coordinates);
			graphics.fill(6, 5, 6 + width + 6, 5 + client.font.lineHeight, PANEL);
			graphics.text(client.font, coordinates, 9, 6, TEXT_COLOR, true);

			Component zoom = Component.translatable("minimap.screen.zoom",
					String.format(Locale.ROOT, "%.2f", blocksPerPixel));
			int zoomWidth = client.font.width(zoom);
			graphics.fill(this.width - zoomWidth - 10, 5, this.width - 4, 5 + client.font.lineHeight, PANEL);
			graphics.text(client.font, zoom, this.width - zoomWidth - 7, 6, TEXT_COLOR, true);
		}

		Component hint = Component.translatable("minimap.screen.hint");
		int hintWidth = client.font.width(hint);
		graphics.fill(this.width / 2 - hintWidth / 2 - 3, this.height - client.font.lineHeight - 6,
				this.width / 2 + hintWidth / 2 + 3, this.height - 4, PANEL);
		graphics.text(client.font, hint, this.width / 2 - hintWidth / 2, this.height - client.font.lineHeight - 5, TEXT_COLOR, true);
	}

	private void drawMarkers(GuiGraphicsExtractor graphics, Minecraft client) {
		Vec3 respawn = MinimapClient.markers().respawn();
		if (MinimapClient.config().showRespawn && respawn != null) {
			double[] screen = worldToScreen(respawn);
			if (inside(screen[0], screen[1])) {
				marker(graphics, client, screen[0], screen[1], RESPAWN_COLOR,
						Component.translatable("minimap.marker.respawn"));
			}
		}
		if (MinimapClient.config().showPlayers) {
			for (Marker marker : MinimapClient.markers().players()) {
				double[] screen = worldToScreen(marker.pos());
				if (inside(screen[0], screen[1])) {
					marker(graphics, client, screen[0], screen[1], OTHER_PLAYER_COLOR, Component.literal(marker.name()));
				}
			}
		}
	}

	private void drawPlayer(GuiGraphicsExtractor graphics, Minecraft client, float partialTick) {
		Vec3 position = client.player.position();
		double[] screen = worldToScreen(position);
		if (!inside(screen[0], screen[1])) {
			return;
		}
		float yaw = client.player.getYRot(partialTick);
		double radians = Math.toRadians(yaw);
		fillTriangle(graphics, screen[0], screen[1], -Math.sin(radians), Math.cos(radians), PLAYER_COLOR);
	}

	private void marker(GuiGraphicsExtractor graphics, Minecraft client, double x, double y, int color, Component label) {
		graphics.fill((int) x - 2, (int) y - 2, (int) x + 3, (int) y + 3, 0xFF000000);
		graphics.fill((int) x - 1, (int) y - 1, (int) x + 2, (int) y + 2, color);
		int labelWidth = client.font.width(label);
		graphics.text(client.font, label, (int) x - labelWidth / 2, (int) y + 5, TEXT_COLOR, true);
	}

	private double[] worldToScreen(Vec3 position) {
		double x = width / 2.0 + offsetX + (position.x - centerX) / blocksPerPixel;
		double y = height / 2.0 + offsetY + (position.z - centerZ) / blocksPerPixel;
		return new double[] {x, y};
	}

	private double screenToWorldX(double screenX) {
		return centerX + (screenX - width / 2.0 - offsetX) * blocksPerPixel;
	}

	private double screenToWorldY(double screenY) {
		return centerZ + (screenY - height / 2.0 - offsetY) * blocksPerPixel;
	}

	private boolean inside(double x, double y) {
		return x >= 0 && x < width && y >= 0 && y < height;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		dragging = true;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			recenter();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			offsetX += dx;
			offsetY += dy;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double factor = scrollY > 0 ? 1.2 : 1.0 / 1.2;
		blocksPerPixel = MinimapMath.clampBlocksPerPixel(blocksPerPixel * factor,
				MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_M || key == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private void centerOn(double x, double z) {
		centerX = x;
		centerZ = z;
		offsetX = 0;
		offsetY = 0;
	}

	/** Moves the map centre to the middle of the screen after a drag. */
	private void recenter() {
		centerX += offsetX * blocksPerPixel;
		centerZ += offsetY * blocksPerPixel;
		offsetX = 0;
		offsetY = 0;
	}

	private static void fillTriangle(GuiGraphicsExtractor graphics, double centerX, double centerY, double dirX, double dirY, int color) {
		double length = Math.sqrt(dirX * dirX + dirY * dirY);
		if (length == 0) {
			return;
		}
		double unitX = dirX / length;
		double unitY = dirY / length;
		double perpX = -unitY;
		double perpY = unitX;
		double size = 9;
		double tipX = centerX + unitX * size;
		double tipY = centerY + unitY * size;
		double baseX = centerX - unitX * size * 0.6;
		double baseY = centerY - unitY * size * 0.6;
		double leftX = baseX + perpX * 5;
		double leftY = baseY + perpY * 5;
		double rightX = baseX - perpX * 5;
		double rightY = baseY - perpY * 5;

		int minY = (int) Math.floor(Math.min(tipY, Math.min(leftY, rightY)));
		int maxY = (int) Math.ceil(Math.max(tipY, Math.max(leftY, rightY)));
		for (int py = minY; py <= maxY; py++) {
			int minX = (int) Math.floor(Math.min(tipX, Math.min(leftX, rightX)));
			int maxX = (int) Math.ceil(Math.max(tipX, Math.max(leftX, rightX)));
			for (int px = minX; px <= maxX; px++) {
				if (insideTriangle(px + 0.5, py + 0.5, tipX, tipY, leftX, leftY, rightX, rightY)) {
					graphics.fill(px, py, px + 1, py + 1, color);
				}
			}
		}
	}

	private static boolean insideTriangle(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
		double d1 = sign(px, py, ax, ay, bx, by);
		double d2 = sign(px, py, bx, by, cx, cy);
		double d3 = sign(px, py, cx, cy, ax, ay);
		boolean hasNegative = d1 < 0 || d2 < 0 || d3 < 0;
		boolean hasPositive = d1 > 0 || d2 > 0 || d3 > 0;
		return !(hasNegative && hasPositive);
	}

	private static double sign(double px, double py, double ax, double ay, double bx, double by) {
		return (px - bx) * (ay - by) - (ax - bx) * (py - by);
	}
}
