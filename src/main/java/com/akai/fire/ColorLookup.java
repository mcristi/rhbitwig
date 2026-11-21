package com.akai.fire;

import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.api.Color;

public class ColorLookup {

	private static float SATURATION_SUBTLE_BOOST = 1.3f;
	private static float SATURATION_MEDIUM_BOOST = 1.5f;
	private static float SATURATION_VIBRANT_BOOST = 2.0f;

	// Step colors by velocity: the normal velocity shows the color itself. Quieter notes fade
	// linearly to a dark, grayish version of it (like the notes in Bitwig's piano roll), louder
	// notes get darker and more saturated up to the accent velocity.
	// part of the color left at velocity 0
	private static float VELOCITY_MIN_COLOR = 0.15f;
	// gray (0..1) mixed in at velocity 0
	private static float VELOCITY_MIN_GRAY = 0.19f;
	// brightness left at the accent velocity
	private static float VELOCITY_ACCENT_BRIGHTNESS = 0.55f;
	// how much the saturation is pushed at the accent velocity
	private static float VELOCITY_ACCENT_SATURATION_BOOST = 1.5f;

	public static final RgbLigthState getColor(final double r, final double g, final double b) {
		int red = (int)(r * 127);
		int green = (int)(g * 127);
		int blue = (int)(b * 127);

		// Boost saturation
		float[] hsv = rgbToHsv(red, green, blue);
		hsv[1] = Math.min(1.0f, hsv[1] * SATURATION_MEDIUM_BOOST);
		int[] saturated = hsvToRgb(hsv[0], hsv[1], hsv[2]);

		return new RgbLigthState(saturated[0], saturated[1], saturated[2], true);
	}

	public static RgbLigthState getColor(final Color color) {
		return getColor(color.getRed(), color.getGreen(), color.getBlue());
	}

	/**
	 * Variant of a color for a note velocity: the normal velocity gives the color itself. Lower
	 * velocities fade linearly to a dark, grayish version of it, higher velocities get darker and
	 * more saturated, reaching the deepest tone at the accent velocity.
	 *
	 * @param velocity note velocity 0..1
	 * @param normalVelocity velocity (0..1, exclusive) that maps to the unchanged color
	 * @param accentVelocity velocity (above normalVelocity) from which the color is at its deepest
	 */
	public static RgbLigthState getVelocityColor(final RgbLigthState base, final double velocity,
			final double normalVelocity, final double accentVelocity) {
		final float vel = (float) Math.max(0, Math.min(1, velocity));
		final float normal = (float) normalVelocity;
		final float accent = (float) Math.max(accentVelocity, normalVelocity + 1 / 127.0);
		float red = base.getRed();
		float green = base.getGreen();
		float blue = base.getBlue();

		if (vel <= normal) {
			final float t = vel / normal;
			final float colorPart = VELOCITY_MIN_COLOR + (1.0f - VELOCITY_MIN_COLOR) * t;
			final float gray = VELOCITY_MIN_GRAY * (1.0f - t) * 127;
			red = red * colorPart + gray;
			green = green * colorPart + gray;
			blue = blue * colorPart + gray;
		} else {
			final float t = Math.min(1.0f, (vel - normal) / (accent - normal));
			// push the weaker channels away from the strongest one (more saturation), then darken
			final float max = Math.max(red, Math.max(green, blue));
			final float spread = 1.0f + (VELOCITY_ACCENT_SATURATION_BOOST - 1.0f) * t;
			final float brightness = 1.0f + (VELOCITY_ACCENT_BRIGHTNESS - 1.0f) * t;
			red = Math.max(0, max - (max - red) * spread) * brightness;
			green = Math.max(0, max - (max - green) * spread) * brightness;
			blue = Math.max(0, max - (max - blue) * spread) * brightness;
		}

		return new RgbLigthState(Math.round(red), Math.round(green), Math.round(blue), true);
	}

	private static float[] rgbToHsv(int r, int g, int b) {
		float rf = r / 127f;
		float gf = g / 127f;
		float bf = b / 127f;

		float max = Math.max(rf, Math.max(gf, bf));
		float min = Math.min(rf, Math.min(gf, bf));
		float delta = max - min;

		float h = 0, s = 0, v = max;

		if (delta > 0) {
			s = delta / max;
			if (rf == max) h = (gf - bf) / delta + (gf < bf ? 6 : 0);
			else if (gf == max) h = (bf - rf) / delta + 2;
			else h = (rf - gf) / delta + 4;
			h /= 6;
		}

		return new float[]{h, s, v};
	}

	private static int[] hsvToRgb(float h, float s, float v) {
		int i = (int)(h * 6);
		float f = h * 6 - i;
		float p = v * (1 - s);
		float q = v * (1 - f * s);
		float t = v * (1 - (1 - f) * s);

		float r, g, b;
		switch (i % 6) {
			case 0: r = v; g = t; b = p; break;
			case 1: r = q; g = v; b = p; break;
			case 2: r = p; g = v; b = t; break;
			case 3: r = p; g = q; b = v; break;
			case 4: r = t; g = p; b = v; break;
			default: r = v; g = p; b = q; break;
		}

		return new int[]{(int)(r * 127), (int)(g * 127), (int)(b * 127)};
	}

}
