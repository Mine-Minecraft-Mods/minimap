package dev.luisf.minimap.hud;

import dev.luisf.minimap.MinimapClient;
import dev.luisf.minimap.MinimapClient.Marker;
import dev.luisf.minimap.config.MinimapConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The corner minimap: a square (or circle) top-down view centred on the player, drawn in the
 * bottom-right corner with a border, a compass, the player arrow, coordinates and markers.
 * Only reads the current cache and marker snapshot.
 */
public final class MinimapHud {
	private static final int MARGIN = 4;
	private static final int BORDER = 1;
	private static final int BACKGROUND = 0x88000000;
	private static final int BORDER_COLOR = 0xFF101010;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int SHADOW_COLOR = 0xFF000000;
	private static final int PLAYER_COLOR = 0xFFFFFFFF;
	private static final int RESPAWN_COLOR = 0xFFFFD83D;
	private static final int OTHER_PLAYER_COLOR = 0xFF4FC3F7;
	private static final int ARROW_SIZE = 7;
	private static final int ARROW_HALF_WIDTH = 4;

	private MinimapHud() {}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		MinimapClient.safely("hud", () -> {
			Minecraft client = Minecraft.getInstance();
			MinimapConfig config = MinimapClient.config();
			if (client.level == null || client.player == null || client.gui.hud.isHidden()
					|| MinimapClient.miniTexture() == null) {
				return;
			}

			int size = config.miniSize;
			int right = graphics.guiWidth() - MARGIN;
			int bottom = graphics.guiHeight() - MARGIN;
			int x = right - size;
			int y = bottom - size;

			if (!MinimapClient.isAllowedHere(client)) {
				Component line = Component.translatable("minimap.hud.disabled_server");
				int width = client.font.width(line);
				graphics.fill(x, y, right, bottom, BACKGROUND);
				graphics.text(client.font, line, x + (size - width) / 2, y + size / 2 - client.font.lineHeight / 2, TEXT_COLOR, true);
				return;
			}

			Entity camera = client.getCameraEntity();
			float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
			double yaw = camera != null ? camera.getYRot(partialTick) : client.player.getYRot();
			Vec3 center = camera != null ? camera.getPosition(partialTick) : client.player.position();

			graphics.fill(x, y, right, bottom, BACKGROUND);
			Identifier texture = MinimapClient.miniTexture().id();
			graphics.blit(texture, x, y, size, size, 0.0F, 0.0F, 1.0F, 1.0F);
			drawBorder(graphics, x, y, size);

			if (config.showCompass) {
				drawCompass(graphics, client, x, y, size, yaw);
			}
			drawPlayerArrow(graphics, x, y, size, yaw, center);
			drawMarkers(graphics, x, y, size, yaw, center);
			if (config.showCoordinates) {
				drawCoordinates(graphics, client, x, y, center);
			}
		});
	}

	private static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int size) {
		graphics.fill(x - BORDER, y - BORDER, x + size + BORDER, y, BORDER_COLOR);
		graphics.fill(x - BORDER, y + size, x + size + BORDER, y + size + BORDER, BORDER_COLOR);
		graphics.fill(x - BORDER, y, x, y + size, BORDER_COLOR);
		graphics.fill(x + size, y, x + size + BORDER, y + size, BORDER_COLOR);
	}

	/** N, E, S, W on the four edges, rotated with the camera when the minimap rotates. */
	private static void drawCompass(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int size, double yaw) {
		Font font = client.font;
		int half = size / 2;
		drawCardinal(graphics, font, "minimap.compass.n", cardinalOffset(yaw, 0, -1), x, y, half);
		drawCardinal(graphics, font, "minimap.compass.s", cardinalOffset(yaw, 0, 1), x, y, half);
		drawCardinal(graphics, font, "minimap.compass.e", cardinalOffset(yaw, 1, 0), x, y, half);
		drawCardinal(graphics, font, "minimap.compass.w", cardinalOffset(yaw, -1, 0), x, y, half);
	}

	private static int[] cardinalOffset(double yaw, int dirX, int dirZ) {
		double radians = Math.toRadians(yaw);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		// Screen position of a world direction: the inverse of MinimapMath#worldOffset.
		int screenX = (int) Math.round(-cos * dirX - sin * dirZ);
		int screenY = (int) Math.round(sin * dirX - cos * dirZ);
		return new int[] {screenX, screenY};
	}

	private static void drawCardinal(GuiGraphicsExtractor graphics, Font font, String key, int[] direction,
			int x, int y, int half) {
		Component label = Component.translatable(key);
		int width = font.width(label);
		int distance = half - font.lineHeight / 2 - 2;
		int labelX = x + half + direction[0] * distance - width / 2;
		int labelY = y + half + direction[1] * distance - font.lineHeight / 2;
		graphics.text(font, label, labelX + 1, labelY + 1, SHADOW_COLOR, false);
		graphics.text(font, label, labelX, labelY, TEXT_COLOR, true);
	}

	private static void drawPlayerArrow(GuiGraphicsExtractor graphics, int x, int y, int size, double yaw, Vec3 center) {
		int centerX = x + size / 2;
		int centerY = y + size / 2;
		boolean rotate = MinimapClient.config().rotate;
		double radians = Math.toRadians(yaw);
		double dirX;
		double dirY;
		if (rotate) {
			// The map rotates with the camera: the player always looks up.
			dirX = 0;
			dirY = -1;
		} else {
			dirX = -Math.sin(radians);
			dirY = Math.cos(radians);
		}
		fillTriangle(graphics, centerX, centerY, dirX, dirY, PLAYER_COLOR);
	}

	private static void fillTriangle(GuiGraphicsExtractor graphics, int centerX, int centerY, double dirX, double dirY, int color) {
		double length = Math.sqrt(dirX * dirX + dirY * dirY);
		if (length == 0) {
			return;
		}
		double unitX = dirX / length;
		double unitY = dirY / length;
		double perpX = -unitY;
		double perpY = unitX;

		double tipX = centerX + unitX * ARROW_SIZE;
		double tipY = centerY + unitY * ARROW_SIZE;
		double baseX = centerX - unitX * ARROW_SIZE * 0.6;
		double baseY = centerY - unitY * ARROW_SIZE * 0.6;
		double leftX = baseX + perpX * ARROW_HALF_WIDTH;
		double leftY = baseY + perpY * ARROW_HALF_WIDTH;
		double rightX = baseX - perpX * ARROW_HALF_WIDTH;
		double rightY = baseY - perpY * ARROW_HALF_WIDTH;

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

	private static void drawMarkers(GuiGraphicsExtractor graphics, int x, int y, int size, double yaw, Vec3 center) {
		MinimapConfig config = MinimapClient.config();
		int half = size / 2;
		double scale = config.miniRadius * 2.0 / size;
		boolean rotate = config.rotate;
		double radians = Math.toRadians(yaw);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);

		if (config.showRespawn && MinimapClient.markers().respawn() != null) {
			int[] screen = worldToScreen(center, MinimapClient.markers().respawn(), scale, rotate, sin, cos);
			if (screen != null) {
				diamond(graphics, x + half + screen[0], y + half + screen[1], RESPAWN_COLOR);
			}
		}
		if (config.showPlayers) {
			for (Marker marker : MinimapClient.markers().players()) {
				int[] screen = worldToScreen(center, marker.pos(), scale, rotate, sin, cos);
				if (screen != null) {
					diamond(graphics, x + half + screen[0], y + half + screen[1], OTHER_PLAYER_COLOR);
				}
			}
		}
	}

	/** Pixel offset from the minimap centre, or {@code null} when outside the map. */
	private static int[] worldToScreen(Vec3 center, Vec3 target, double scale, boolean rotate, double sin, double cos) {
		double dx = target.x - center.x;
		double dz = target.z - center.z;
		double screenX;
		double screenY;
		if (rotate) {
			screenX = -cos * dx - sin * dz;
			screenY = sin * dx - cos * dz;
		} else {
			screenX = dx;
			screenY = dz;
		}
		int px = (int) Math.round(screenX / scale);
		int py = (int) Math.round(screenY / scale);
		if (Math.abs(px) > MinimapClient.config().miniSize / 2 || Math.abs(py) > MinimapClient.config().miniSize / 2) {
			return null;
		}
		return new int[] {px, py};
	}

	private static void diamond(GuiGraphicsExtractor graphics, int x, int y, int color) {
		graphics.fill(x - 1, y - 2, x + 1, y - 1, SHADOW_COLOR);
		graphics.fill(x - 2, y - 1, x + 2, y + 1, SHADOW_COLOR);
		graphics.fill(x - 1, y + 1, x + 1, y + 2, SHADOW_COLOR);
		graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
		graphics.fill(x, y - 2, x + 1, y - 1, color);
		graphics.fill(x, y + 1, x + 1, y + 2, color);
		graphics.fill(x - 2, y, x - 1, y + 1, color);
		graphics.fill(x + 1, y, x + 2, y + 1, color);
	}

	private static void drawCoordinates(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, Vec3 center) {
		Component text = Component.translatable("minimap.hud.coordinates",
				MinimapClient.config().miniRadius, (int) Math.floor(center.x), (int) Math.floor(center.z));
		int width = client.font.width(text);
		int textX = x + (MinimapClient.config().miniSize - width) / 2;
		int textY = y - client.font.lineHeight - 1;
		graphics.fill(textX - 2, textY - 1, textX + width + 2, textY + client.font.lineHeight, BACKGROUND);
		graphics.text(client.font, text, textX, textY, TEXT_COLOR, true);
	}
}
