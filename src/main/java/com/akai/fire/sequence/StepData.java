package com.akai.fire.sequence;

import com.bitwig.extension.controller.api.NoteOccurrence;
import com.bitwig.extension.controller.api.NoteStep;

/**
 * The values of a note, read at once. A {@link NoteStep} only stays reliable while its clip and key
 * are the ones on display, so a pattern that has to survive a clip switch is kept as these.
 */
public record StepData(int x, double velocity, double duration, double chance, double timbre, double pressure,
		double pan, double velocitySpread, int repeatCount, double repeatCurve, double repeatVelocityCurve,
		double repeatVelocityEnd, int recurrenceLength, int recurrenceMask, NoteOccurrence occurrence) {

	public static StepData of(final NoteStep note) {
		return new StepData(note.x(), note.velocity(), note.duration(), note.chance(), note.timbre(),
				note.pressure(), note.pan(), note.velocitySpread(), note.repeatCount(), note.repeatCurve(),
				note.repeatVelocityCurve(), note.repeatVelocityEnd(), note.recurrenceLength(),
				note.recurrenceMask(), note.occurrence());
	}

	public int midiVelocity() {
		return Math.max(1, Math.min(127, (int) Math.round(velocity * 127)));
	}

	/** Everything that creating the step (position, velocity, duration) does not already set. */
	public void applyTo(final NoteStep dest) {
		// chance() reads 0 for a note that never had its chance changed, same workaround as applyValues
		dest.setChance(chance == 0 ? 1 : chance);
		dest.setTimbre(timbre);
		dest.setPressure(pressure);
		dest.setPan(pan);
		dest.setVelocitySpread(velocitySpread);
		dest.setRepeatCount(repeatCount);
		dest.setRepeatCurve(repeatCurve);
		dest.setRepeatVelocityCurve(repeatVelocityCurve);
		dest.setRepeatVelocityEnd(repeatVelocityEnd);
		dest.setRecurrence(Math.max(1, recurrenceLength), recurrenceMask);
		dest.setOccurrence(occurrence);
	}
}
