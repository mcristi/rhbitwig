package com.akai.lpd8;

import java.util.function.IntSupplier;

import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extensions.framework.values.Midi;

/**
 * Akai LPD8 mk2 next to the Fire and the MIDI Mix.
 * <ul>
 * <li>Pads in note mode: the notes go to Bitwig as they are.</li>
 * <li>Pads in CC mode (CC 71..78): they play the 8 drum pads the MIDI Mix is on, with the value the
 * pad sends as velocity. If the drum track is armed the hits go in as played notes, so they can be
 * recorded; if it is not, they are played on the track directly, so they are heard anyway.</li>
 * <li>Knobs: not handled here, they are mapped by hand in Bitwig.</li>
 * </ul>
 */
public class Lpd8 {
    // CCs the 8 pads send in CC mode, pad 1..8
    private static final int FIRST_PAD_CC = 71;
    private static final int NUM_PADS = 8;

    private final NoteInput noteInput;
    private final IntSupplier firstKey;
    // per pad: the key it is sounding, so that the note ends on the same key if the pads move while
    // it is held; -1 = not held
    private final int[] soundingKey = new int[NUM_PADS];
    // per pad: how its note was started, the note has to end the same way
    private final boolean[] soundingAsInput = new boolean[NUM_PADS];
    private final Track drumTrack;

    /**
     * @param drumTrack the track with the Drum Machine
     * @param firstKey  the key of the first of the 8 drum pads that the pads play in CC mode
     */
    public Lpd8(final MidiIn midiIn, final Track drumTrack, final IntSupplier firstKey) {
        this.drumTrack = drumTrack;
        this.firstKey = firstKey;
        drumTrack.arm().markInterested();
        java.util.Arrays.fill(soundingKey, -1);

        midiIn.setMidiCallback(this::onMidi);

        noteInput = midiIn.createNoteInput("LPD8 Pads", "8?????", "9?????", "D?????", "E?????");
        noteInput.setShouldConsumeEvents(true);
    }

    private void onMidi(final int status, final int data1, final int data2) {
        if ((status & 0xF0) != Midi.CC) {
            return;
        }
        final int pad = data1 - FIRST_PAD_CC;
        if (pad < 0 || pad >= NUM_PADS) {
            return;
        }
        if (data2 > 0) {
            // further values while the pad is held are pressure, not a new hit
            if (soundingKey[pad] == -1) {
                final int key = firstKey.getAsInt() + pad;
                if (key >= 0 && key <= 127) {
                    soundingKey[pad] = key;
                    // armed: as input, which Bitwig records. Not armed: input would not be heard,
                    // so play it on the track itself (that is not recorded).
                    soundingAsInput[pad] = drumTrack.arm().get();
                    if (soundingAsInput[pad]) {
                        noteInput.sendRawMidiEvent(Midi.NOTE_ON, key, data2);
                    } else {
                        drumTrack.startNote(key, data2);
                    }
                }
            }
        } else {
            endNote(pad);
        }
    }

    private void endNote(final int pad) {
        if (soundingKey[pad] == -1) {
            return;
        }
        if (soundingAsInput[pad]) {
            noteInput.sendRawMidiEvent(Midi.NOTE_OFF, soundingKey[pad], 0);
        } else {
            drumTrack.stopNote(soundingKey[pad], 0);
        }
        soundingKey[pad] = -1;
    }

    /** Ends the notes that are still sounding. */
    public void exit() {
        for (int pad = 0; pad < NUM_PADS; pad++) {
            endNote(pad);
        }
    }
}
