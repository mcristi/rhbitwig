package com.akai.fire.lights;

import java.util.Objects;

import com.akai.fire.ColorLookup;
import com.bitwig.extension.controller.api.HardwareLightVisualState;
import com.bitwig.extension.controller.api.InternalHardwareLightState;

public class RgbLigthState extends InternalHardwareLightState {

	private static final int ULTRA_DIM_FACTOR = 15;
	private static final int DIM_FACTOR = 2;
	private static final int BRIGHT_FACTOR = 50;
	private static final int MAX_BRIGHT_FACTOR = 70;
	private static final int VELOCITY_LEVELS = 128;

	public static final RgbLigthState OFF = new RgbLigthState(0, 0, 0, true);
	public static final RgbLigthState PURPLE = new RgbLigthState(80, 0, 80, true);
	// encoder 1..4, in the order Bitwig colors its remote controls
	public static final RgbLigthState RED = new RgbLigthState(127, 0, 0, true);
	public static final RgbLigthState ORANGE = new RgbLigthState(127, 30, 0, true);
	public static final RgbLigthState YELLOW = new RgbLigthState(127, 100, 0, true);
	public static final RgbLigthState GREEN = new RgbLigthState(0, 127, 0, true);
	// the "if" condition of a step (Fill on, ..)
	// the azure of Bitwig's 6th remote control; pure blue looks purple on the pads
	public static final RgbLigthState BLUE = new RgbLigthState(0, 45, 110, true);
	public static final RgbLigthState WHITE = new RgbLigthState(100, 100, 100, true);
	// as bright as the pads go
	public static final RgbLigthState BRIGHT_WHITE = new RgbLigthState(127, 127, 127, true);
	public static final RgbLigthState GRAY_1 = new RgbLigthState(10, 10, 10, true);
	public static final RgbLigthState GRAY_2 = new RgbLigthState(40, 40, 40, true);

	private final byte red;
	private final byte green;
	private final byte blue;

	private RgbLigthState veryDimmed;
	private RgbLigthState dimmed;
	private RgbLigthState brightend;
	private RgbLigthState brightest;
	private RgbLigthState[] velocityVariants;
	private int velocityVariantsNormal;
	private int velocityVariantsAccent;

	public RgbLigthState(final int red, final int green, final int blue, final boolean variants) {
		this((byte) red, (byte) green, (byte) blue, variants);
	}

	public RgbLigthState(final byte red, final byte green, final byte blue) {
		this(red, green, blue, true);
	}

	private RgbLigthState(final byte red, final byte green, final byte blue, final boolean variants) {
		super();

		this.red = red;
		this.green = green;
		this.blue = blue;

		if (variants) {
			dimmed = new RgbLigthState(red / DIM_FACTOR, green / DIM_FACTOR, blue / DIM_FACTOR, false);
			veryDimmed = new RgbLigthState(red / ULTRA_DIM_FACTOR, green / ULTRA_DIM_FACTOR, blue / ULTRA_DIM_FACTOR,
					false);
			brightend = new RgbLigthState(Math.min(red + BRIGHT_FACTOR, 127), Math.min(green + BRIGHT_FACTOR, 127),
					Math.min(blue + BRIGHT_FACTOR, 127), false);
			brightest = new RgbLigthState(Math.min(red + MAX_BRIGHT_FACTOR, 127),
					Math.min(green + MAX_BRIGHT_FACTOR, 127), Math.min(blue + MAX_BRIGHT_FACTOR, 127), false);
		}
	}

	@Override
	public HardwareLightVisualState getVisualState() {
		return null;
	}

	public RgbLigthState getVeryDimmed() {
		return veryDimmed != null ? veryDimmed : this;
	}

	public RgbLigthState getDimmed() {
		return dimmed != null ? dimmed : this;
	}

	public RgbLigthState getBrightend() {
		return brightend != null ? brightend : this;
	}

	/**
	 * @param velocity note velocity as MIDI value 0..127
	 * @param normalVelocity MIDI velocity that shows this color unchanged
	 * @param accentVelocity MIDI velocity from which the color is at its deepest
	 * @return this color adjusted to the velocity (see ColorLookup.getVelocityColor), cached per velocity
	 */
	public RgbLigthState getForVelocity(final int velocity, final int normalVelocity, final int accentVelocity) {
		final int level = Math.max(0, Math.min(VELOCITY_LEVELS - 1, velocity));
		if (level == normalVelocity) {
			return this;
		}
		if (velocityVariants == null || velocityVariantsNormal != normalVelocity
				|| velocityVariantsAccent != accentVelocity) {
			velocityVariants = new RgbLigthState[VELOCITY_LEVELS];
			velocityVariantsNormal = normalVelocity;
			velocityVariantsAccent = accentVelocity;
		}
		if (velocityVariants[level] == null) {
			velocityVariants[level] = ColorLookup.getVelocityColor(this, level / 127.0, normalVelocity / 127.0,
					accentVelocity / 127.0);
		}
		return velocityVariants[level];
	}

	public RgbLigthState getBrightest() {
		return brightest != null ? brightest : this;
	}

	@Override
	public int hashCode() {
		return Objects.hash(blue, green, red);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final RgbLigthState other = (RgbLigthState) obj;
		return blue == other.blue && green == other.green && red == other.red;
	}

	public byte getRed() {
		return red;
	}

	public byte getBlue() {
		return blue;
	}

	public byte getGreen() {
		return green;
	}

}
