package com.akai.fire.sequence;

import com.akai.fire.AkaiFireDrumSeqExtension;
import com.akai.fire.lights.BiColorLightState;
import com.bitwig.extension.controller.api.Arpeggiator;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extensions.framework.values.BooleanValueObject;

public class NoteRepeatHandler {

	private final DrumSequenceMode parent;
	private final BooleanValueObject noteRepeatActive = new BooleanValueObject();
	private boolean repeatButtonHeld = false;
	private double currentArpRate = ARP_RATES[1];
	private static final double[] ARP_RATES = new double[] { 0.125, 0.25, 0.5, 1.0, 1.0 / 12, 1.0 / 6, 1.0 / 3,
			2.0 / 3 };
	private static final String[] GRID_RATES_STR = new String[] { "1/32", "1/16", "1/8", "1/4", //
			"1/32T", "1/16T", "1/8T", "1/4T" };
	private final Arpeggiator arp;
	private final NoteInput noteInput;
	private int selectedArpIndex;

	public NoteRepeatHandler(final AkaiFireDrumSeqExtension driver, final DrumSequenceMode drumSequenceMode) {
		this.parent = drumSequenceMode;
		this.noteInput = driver.getNoteInput();
		this.setNoteInputVelocity(parent.getAccentHandler().getCurrenVel());
		this.selectedArpIndex = 1;
		arp = noteInput.arpeggiator();
		arp.usePressureToVelocity().set(true);
		// arp.shuffle().set(true);
		arp.mode().set("all"); // that's the note repeat way
		arp.octaves().set(0);
		arp.humanize().set(0);
		arp.isFreeRunning().set(false);
		arp.isEnabled().markInterested();
	}

	BiColorLightState getLightState() {
		return noteRepeatActive.get() ? BiColorLightState.HALF : BiColorLightState.OFF;
	}

	void handlePressed(final boolean pressed) {
		if (pressed) {
			noteRepeatActive.toggle();
			if (noteRepeatActive.get()) {
				parent.getOled().valueInfo("Note Repeat", GRID_RATES_STR[selectedArpIndex]);
			}
		} else {
			parent.getOled().clearScreenDelayed();
		}
		repeatButtonHeld = pressed;
	}

	boolean isHolding() {
		return repeatButtonHeld;
	}

	public BooleanValueObject getNoteRepeatActive() {
		return noteRepeatActive;
	}

	void handleMainEncoder(final int inc) {
 		if (arp.isEnabled().get()) {
			final int newValue = selectedArpIndex + inc;
			if (newValue >= 0 && newValue < ARP_RATES.length) {
				selectedArpIndex = newValue;
				setNoteRateValue(newValue);
			}
		} else {
            if (parent.getPadHandler().selectedPad == null) {
                parent.getOled().paramInfo("Sample", "No selection", "Select a pad first");
                parent.getOled().clearScreenDelayed();
                return;
            }

            String param1Name = parent.getPadHandler().selectedPad.getParam1Name();
            if (param1Name.toLowerCase().contains("tune")) {
                parent.getPadHandler().setTuneValue(inc);
                parent.host.scheduleTask(() -> {
                    parent.getOled().valueInfo("Tune", "" + parent.getPadHandler().getTuneValue());
                    parent.getOled().clearScreenDelayed();
                }, 0);
                return;
            }

            String param2Name = parent.getPadHandler().selectedPad.getParam2Name();
            if (param2Name.toLowerCase().contains("semis")) {
                parent.getPadHandler().setSemisValue(inc);
                parent.host.scheduleTask(() -> {
                    parent.getOled().valueInfo("Semitones", "" + parent.getPadHandler().getSemisValue());
                    parent.getOled().clearScreenDelayed();
                }, 0);
                return;
            }

            String param8Name = parent.getPadHandler().selectedPad.getParam8Name();
            if (param8Name.toLowerCase().contains("select")) {
                parent.getPadHandler().setSampleValue(inc);
                parent.host.scheduleTask(() -> {
                    parent.getOled().valueInfo("Sample#", "" + parent.getPadHandler().getSampleValue());
                    parent.getOled().clearScreenDelayed();
                }, 0);
                return;
            }
		}
	}

	private void setNoteRateValue(final int index) {
		this.selectedArpIndex = index;
		this.currentArpRate = ARP_RATES[index];
		arp.rate().set(currentArpRate);
		parent.getOled().valueInfo("Note Repeat", GRID_RATES_STR[index]);
	}

	public void activate() {
		arp.isEnabled().set(true);
		arp.mode().set("all"); // that's the note repeat way
		arp.octaves().set(0);
		arp.humanize().set(0);
		arp.isFreeRunning().set(false);
		arp.rate().set(currentArpRate);
	}

	public void deactivate() {
		arp.isEnabled().set(false);
	}

	public void setNoteInputVelocity(final int velocity) {
		// NOTE: note repeat velocity
		final Integer[] notesToDrumTable = new Integer[128];
		for (int i = 0; i < notesToDrumTable.length; i++) {
			notesToDrumTable[i] = velocity;
		}
		noteInput.setVelocityTranslationTable(notesToDrumTable);
	}
}
