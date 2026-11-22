package com.akai.fire.sequence;

import java.util.List;

import com.akai.fire.DrumSequencerExtension;
import com.akai.fire.control.RgbButton;
import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.controller.api.NoteOccurrence;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extensions.framework.Layer;

/**
 * Shows and edits the recurrence pattern on the second pad row. Once opened it stays until all steps
 * are released and always shows the step pressed last, so holding another step switches to its
 * pattern. A pad press changes the pattern of all held steps.
 * <p>
 * The last pad of the row switches the step's "if" condition between Fill on (blue: the step only
 * plays while Bitwig's fill mode is on) and Always (grey).
 */
public class RecurrenceEditor {
	private final DrumSequenceMode parent;
	private final Layer layer;
	private boolean pendingRelease;
	// cycles of the pattern that are switched off: white enough to stand out from unused pads
	private static final RgbLigthState CYCLE_OFF = new RgbLigthState(55, 55, 55, true);
	// the pad that switches Fill on / Always
	private static final int FILL_PAD = 15;

	public RecurrenceEditor(final DrumSequencerExtension driver, final DrumSequenceMode parent) {
		this.parent = parent;
		layer = new Layer(driver.getLayers(), parent.getName() + "_RECURRENCE");
		initClipControlButtons(layer, driver);
		final IntSetValue heldSteps = parent.getHeldSteps();
		heldSteps.addSizeValueListener(stepsHeld -> finalizeEdit(stepsHeld));
	}

	private void finalizeEdit(final int stepsHeld) {
		if (pendingRelease && stepsHeld == 0) {
			abort();
		}
	}

	private void initClipControlButtons(final Layer clipLayer, final DrumSequencerExtension driver) {
		final RgbButton[] rgbButtons = driver.getRgbButtons();
		for (int i = 0; i < 16; i++) {
			final RgbButton button = rgbButtons[i + 16];
			final int index = i;
			final int mask = 0x1 << i;
			button.bindPressed(clipLayer, p -> handleMask(p, index, mask), () -> getState(index, mask));
		}
	}

	private RgbLigthState getState(final int index, final int mask) {
		final NoteStep note = parent.getFocusNote();
		if (note == null) {
			return RgbLigthState.OFF;
		}
		if (index == FILL_PAD) {
			return note.occurrence() == NoteOccurrence.FILL ? RgbLigthState.BLUE : RgbLigthState.GRAY_2;
		}
		final int length = note.recurrenceLength();
		if (index < length && length > 1) {
			if ((mask & note.recurrenceMask()) != 0) {
				return RgbLigthState.GREEN; // encoder 4
			}
			return CYCLE_OFF;
		}
		return RgbLigthState.OFF;
	}

	private void handleMask(final boolean pressed, final int index, final int mask) {
		final NoteStep focus = parent.getFocusNote();
		if (pressed && focus != null && index == FILL_PAD) {
			// what the pad shows, toggled, goes to all held steps
			final NoteOccurrence occurrence = focus.occurrence() == NoteOccurrence.FILL ? NoteOccurrence.ALWAYS
					: NoteOccurrence.FILL;
			for (final NoteStep note : parent.getHeldNotes()) {
				note.setOccurrence(occurrence);
			}
			parent.getOled().paramInfo("IF:", occurrence == NoteOccurrence.FILL ? "Fill on" : "Always",
					parent.getDetails(parent.getHeldNotes()));
			return;
		}
		if (!pressed || focus == null || index >= focus.recurrenceLength()) {
			return;
		}
		// the shown pattern with this pad toggled goes to all held steps
		final int length = focus.recurrenceLength();
		final int newMask = focus.recurrenceMask() ^ mask;
		final List<NoteStep> notes = parent.getHeldNotes();
		for (final NoteStep note : notes) {
			note.setRecurrence(length, newMask);
		}
	}

	/** Leaves the editor immediately, without waiting for the encoder and steps to be released. */
	public void abort() {
		pendingRelease = false;
		layer.deactivate();
	}

	public void exitRecurrenceEdit() {
		pendingRelease = true;
	}

	public void enterRecurrenceEdit() {
		pendingRelease = false;
		layer.activate();
	}

}
