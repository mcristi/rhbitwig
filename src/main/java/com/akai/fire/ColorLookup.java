package com.akai.fire;

import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.api.Color;

public class ColorLookup {

	private static float SATURATION_SUBTLE_BOOST = 1.3f;
	private static float SATURATION_MEDIUM_BOOST = 1.5f;
	private static float SATURATION_VIBRANT_BOOST = 2.0f;

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
