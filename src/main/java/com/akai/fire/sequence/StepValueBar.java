package com.akai.fire.sequence;

import java.util.List;

import com.akai.fire.DrumSequencerExtension;
import com.akai.fire.control.RgbButton;
import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extensions.framework.Layer;

/**
 * Shows a value of the held step as a bar on the second pad row and lets a pad press set the value
 * for all held steps. Once opened by touching the encoder it stays until all steps are released and
 * always shows the step pressed last, so holding another step switches to its value. The last pad
 * of the bar is dimmed if the value lies between two pads. Colors follow the encoder position like
 * Bitwig's remote controls.
 * <ul>
 * <li>Velocity (red): 16 pads, 8 per pad, pad 16 = 127</li>
 * <li>Chance (orange): 16 pads, pad 4 = 25%, pad 8 = 50%, pad 16 = 100%</li>
 * <li>Repeats (yellow): as many pads as repeats, nothing = off, pad 1 switches them off</li>
 * </ul>
 */
public class StepValueBar {
	private static final int PADS = 16;
	private static final int VELOCITY_PER_PAD = 8;
	private static final int CHANCE_PADS = PADS;

	private final DrumSequenceMode parent;
	private final Layer layer;
	private NoteStepAccess active;

	public StepValueBar(final DrumSequencerExtension driver, final DrumSequenceMode parent) {
		this.parent = parent;
		layer = new Layer(driver.getLayers(), parent.getName() + "_VALUE_BAR");
		final RgbButton[] rgbButtons = driver.getRgbButtons();
		for (int i = 0; i < PADS; i++) {
			final int index = i;
			rgbButtons[i + PADS].bindPressed(layer, pressed -> handlePad(pressed, index), () -> getState(index));
		}
		// the bar belongs to the held steps, it goes away with them
		parent.getHeldSteps().addSizeValueListener(stepsHeld -> {
			if (stepsHeld == 0) {
				abort();
			}
		});
	}

	public static boolean supports(final NoteStepAccess accessor) {
		return accessor == NoteStepAccess.VELOCITY || accessor == NoteStepAccess.CHANCE
				|| accessor == NoteStepAccess.REPEATS;
	}

	/** Bitwig counts repeats as Off, 2, 3, ...: the API value is one lower. */
	public static String repeatsText(final int repeatCount) {
		return repeatCount <= 0 ? "Off" : Integer.toString(repeatCount + 1);
	}

	private RgbLigthState color() {
		switch (active) {
		case VELOCITY:
			return RgbLigthState.RED;
		case CHANCE:
			return RgbLigthState.ORANGE;
		default:
			return RgbLigthState.YELLOW;
		}
	}

	/** @return how many pads the value of the note fills, fractions included */
	private double filledPads(final NoteStep note) {
		switch (active) {
		case VELOCITY: {
			final int velocity = active.getInt(note);
			return velocity <= 0 ? 0 : (velocity + 1) / (double) VELOCITY_PER_PAD;
		}
		case CHANCE:
			// rounded, so that e.g. 0.3 from summed-up encoder steps still fills exactly 3 pads
			return Math.round(note.chance() * CHANCE_PADS * 1000) / 1000.0;
		default:
			return note.repeatCount() <= 0 ? 0 : note.repeatCount() + 1;
		}
	}

	private RgbLigthState getState(final int index) {
		final NoteStep note = active == null ? null : parent.getFocusNote();
		if (note == null) {
			return RgbLigthState.OFF;
		}
		final double filled = filledPads(note);
		if (filled >= index + 1) {
			return color();
		}
		if (filled > index) {
			return color().getVeryDimmed();
		}
		return RgbLigthState.OFF;
	}

	private void handlePad(final boolean pressed, final int index) {
		final List<NoteStep> editedSteps = parent.getHeldNotes();
		if (!pressed || active == null || editedSteps.isEmpty()) {
			return;
		}
		final String details = parent.getDetails(editedSteps);
		switch (active) {
		case VELOCITY: {
			final int velocity = (index + 1) * VELOCITY_PER_PAD - 1;
			editedSteps.forEach(note -> note.setVelocity(velocity / 127.0));
			parent.getOled().paramInfo(active.getName(), velocity, details, active.getMinInt(), active.getMaxInt());
			break;
		}
		case CHANCE: {
			if (index >= CHANCE_PADS) {
				return;
			}
			final double chance = (index + 1) / (double) CHANCE_PADS;
			editedSteps.forEach(note -> note.setChance(chance));
			parent.getOled().paramInfoPercent(active.getName(), chance, details, active.getMin(), active.getMax());
			break;
		}
		default: {
			if (index > active.getMaxInt()) {
				return;
			}
			// pad 1 = off, pad n = n repeats
			final int repeatCount = index;
			editedSteps.forEach(note -> note.setRepeatCount(repeatCount));
			parent.getOled().paramInfo(active.getName(), repeatCount, repeatsText(repeatCount), details,
					active.getMinInt(), active.getMaxInt());
			break;
		}
		}
	}

	public void enter(final NoteStepAccess accessor) {
		if (supports(accessor)) {
			active = accessor;
			layer.activate();
		}
	}

	public void abort() {
		active = null;
		layer.deactivate();
	}
}
