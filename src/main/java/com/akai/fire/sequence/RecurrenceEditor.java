package com.akai.fire.sequence;

import java.util.List;

import com.akai.fire.AkaiFireDrumSeqExtension;
import com.akai.fire.control.RgbButton;
import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extensions.framework.Layer;

/**
 * Shows and edits the recurrence pattern on the second pad row. Once opened it stays until all steps
 * are released and always shows the step pressed last, so holding another step switches to its
 * pattern. A pad press changes the pattern of all held steps.
 */
public class RecurrenceEditor {
	private final DrumSequenceMode parent;
	private final Layer layer;
	private boolean pendingRelease;

	public RecurrenceEditor(final AkaiFireDrumSeqExtension driver, final DrumSequenceMode parent) {
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

	private void initClipControlButtons(final Layer clipLayer, final AkaiFireDrumSeqExtension driver) {
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
		final int length = note.recurrenceLength();
		if (index < length && length > 1) {
			if ((mask & note.recurrenceMask()) != 0) {
				return RgbLigthState.GREEN; // encoder 4
			}
			return RgbLigthState.GRAY_1;
		}
		return RgbLigthState.OFF;
	}

	private void handleMask(final boolean pressed, final int index, final int mask) {
		final NoteStep focus = parent.getFocusNote();
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
