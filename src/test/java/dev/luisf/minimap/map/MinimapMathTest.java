package dev.luisf.minimap.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinimapMathTest {
	@Test
	void wrapsDegreesToSignedHalfTurn() {
		assertEquals(0.0, MinimapMath.wrapDegrees(0.0), 1e-9);
		assertEquals(180.0, MinimapMath.wrapDegrees(180.0), 1e-9);
		// Both 180 and -180 are the same direction; the helper normalises to 180.
		assertEquals(180.0, MinimapMath.wrapDegrees(-180.0), 1e-9);
		assertEquals(-90.0, MinimapMath.wrapDegrees(270.0), 1e-9);
		assertEquals(90.0, MinimapMath.wrapDegrees(-270.0), 1e-9);
		assertEquals(10.0, MinimapMath.wrapDegrees(370.0), 1e-9);
	}

	@Test
	void yawTowardsUsesMinecraftConvention() {
		// yaw 0 looks at +Z (south), 90 at -X (west), 180 at -Z (north), -90 at +X (east).
		assertEquals(0.0, MinimapMath.yawTowards(0, 1), 1e-9);
		assertEquals(90.0, MinimapMath.yawTowards(-1, 0), 1e-9);
		assertEquals(180.0, MinimapMath.wrapDegrees(MinimapMath.yawTowards(0, -1)), 1e-9);
		assertEquals(-90.0, MinimapMath.yawTowards(1, 0), 1e-9);
	}

	@Test
	void relativeAngleIsNegativeToTheLeft() {
		// Looking south (yaw 0), a target to the east (+X) is to the left.
		assertEquals(-90.0, MinimapMath.relativeAngle(0, 1, 0), 1e-9);
		// Looking west (yaw 90), a target to the south (+Z) is to the left.
		assertEquals(-90.0, MinimapMath.relativeAngle(90, 0, 1), 1e-9);
		// Looking west (yaw 90), a target to the north (-Z) is to the right.
		assertEquals(90.0, MinimapMath.relativeAngle(90, 0, -1), 1e-9);
	}

	@Test
	void fixedMapHasNorthUp() {
		double[] up = MinimapMath.worldOffset(0, -1, 45, false);
		double[] right = MinimapMath.worldOffset(1, 0, 45, false);
		assertEquals(0.0, up[0], 1e-9);
		assertEquals(-1.0, up[1], 1e-9);
		assertEquals(1.0, right[0], 1e-9);
		assertEquals(0.0, right[1], 1e-9);
	}

	@Test
	void rotatedMapLooksForwardAndRightIsWestWhenFacingSouth() {
		// yaw 0 faces south (+Z): up on screen is +Z, right on screen is -X (west).
		double[] up = MinimapMath.worldOffset(0, -1, 0, true);
		double[] right = MinimapMath.worldOffset(1, 0, 0, true);
		assertEquals(0.0, up[0], 1e-9);
		assertEquals(1.0, up[1], 1e-9);
		assertEquals(-1.0, right[0], 1e-9);
		assertEquals(0.0, right[1], 1e-9);
	}

	@Test
	void rotatedMapFacesWestWhenYawIs90() {
		// yaw 90 faces west (-X): up on screen is -X, right on screen is -Z (north).
		double[] up = MinimapMath.worldOffset(0, -1, 90, true);
		double[] right = MinimapMath.worldOffset(1, 0, 90, true);
		assertEquals(-1.0, up[0], 1e-9);
		assertEquals(0.0, up[1], 1e-9);
		assertEquals(0.0, right[0], 1e-9);
		assertEquals(-1.0, right[1], 1e-9);
	}

	@Test
	void heightShadeIsBrighterHigherAndClamped() {
		float atSea = MinimapMath.heightShade(64, 64);
		assertEquals(1.0f, atSea, 1e-6);
		assertTrue(MinimapMath.heightShade(200, 64) > atSea);
		assertTrue(MinimapMath.heightShade(-60, 64) < atSea);
		assertEquals(1.30f, MinimapMath.heightShade(10_000, 64), 1e-6);
		assertEquals(0.70f, MinimapMath.heightShade(-10_000, 64), 1e-6);
	}

	@Test
	void scaleRgbKeepsAlphaAndClamps() {
		assertEquals(0xFF808080, MinimapMath.scaleRgb(0xFF808080, 1.0f));
		assertEquals(0xFF000000, MinimapMath.scaleRgb(0xFF000000, 2.0f));
		assertEquals(0xFF404040, MinimapMath.scaleRgb(0xFF808080, 0.5f));
	}

	@Test
	void zoomIsClamped() {
		assertEquals(0.25, MinimapMath.clampBlocksPerPixel(0.1, 0.25, 32), 1e-9);
		assertEquals(32.0, MinimapMath.clampBlocksPerPixel(99, 0.25, 32), 1e-9);
	}
}
