package dev.luisf.minimap.map;

/**
 * Pure coordinate and colour math for the minimap. No Minecraft dependencies, so it can be
 * unit-tested.
 *
 * <p>Minecraft's yaw convention: 0° looks at +Z (south), 90° at −X (west), ±180° at −Z
 * (north), −90° at +X (east).
 */
public final class MinimapMath {
	private MinimapMath() {}

	public static int clamp(int value, int min, int max) {
		return Math.clamp(value, min, max);
	}

	public static double clamp(double value, double min, double max) {
		return Math.clamp(value, min, max);
	}

	/** Angle in (−180°, 180°]. */
	public static double wrapDegrees(double degrees) {
		double wrapped = degrees % 360.0;
		if (wrapped <= -180.0) {
			wrapped += 360.0;
		} else if (wrapped > 180.0) {
			wrapped -= 360.0;
		}
		return wrapped;
	}

	/** Yaw that looks from the origin towards the offset {@code (dx, dz)}. */
	public static double yawTowards(double dx, double dz) {
		return Math.toDegrees(Math.atan2(-dx, dz));
	}

	/**
	 * Where a target appears relative to where the player is looking: negative = to the left,
	 * positive = to the right, in degrees.
	 */
	public static double relativeAngle(double playerYaw, double dx, double dz) {
		return wrapDegrees(yawTowards(dx, dz) - playerYaw);
	}

	/**
	 * World offset, in blocks, for a point of the map given in "screen" units where +x is to
	 * the right and +y is down, relative to the centre of the map.
	 *
	 * @param rotate when false, north (−Z) is up and east (+X) is right, as on a paper map
	 */
	public static double[] worldOffset(double screenX, double screenY, double yawDegrees, boolean rotate) {
		if (!rotate) {
			return new double[] {screenX, screenY};
		}
		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		// forward = (−sin, cos); right = (−cos, −sin); up on screen is −screenY.
		double dx = -cos * screenX + sin * screenY;
		double dz = -sin * screenX - cos * screenY;
		return new double[] {dx, dz};
	}

	/**
	 * Brightness factor from the block height: higher ground is brighter, lower ground is
	 * darker. {@code seaLevel} keeps the scale stable across dimensions.
	 */
	public static float heightShade(int height, int seaLevel) {
		return (float) clamp(1.0 + (height - seaLevel) * 0.006, 0.70, 1.30);
	}

	/** Multiplies the RGB channels of {@code argb} by {@code factor}, keeping the alpha. */
	public static int scaleRgb(int argb, float factor) {
		int alpha = argb & 0xFF000000;
		int red = (int) Math.clamp(((argb >> 16) & 0xFF) * factor, 0, 255);
		int green = (int) Math.clamp(((argb >> 8) & 0xFF) * factor, 0, 255);
		int blue = (int) Math.clamp((argb & 0xFF) * factor, 0, 255);
		return alpha | (red << 16) | (green << 8) | blue;
	}

	/** Clamps a zoom expressed as blocks per pixel. */
	public static double clampBlocksPerPixel(double blocksPerPixel, double min, double max) {
		return clamp(blocksPerPixel, min, max);
	}

	/** Formats a coordinate for the HUD. */
	public static String coordinate(int value) {
		return Integer.toString(value);
	}
}
