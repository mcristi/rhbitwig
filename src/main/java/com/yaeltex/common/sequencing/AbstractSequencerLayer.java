package com.yaeltex.common.sequencing;

import java.util.HashSet;
import java.util.Set;

import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.Layers;
import com.bitwig.extensions.framework.values.StepViewPosition;
import com.yaeltex.common.YaeltexButtonLedState;
import com.yaeltex.common.YaeltexMidiProcessor;

public abstract class AbstractSequencerLayer extends Layer {
    protected final NotesState operatorNoteState;
    protected final StepViewPosition positionHandler;
    protected final YaeltexMidiProcessor midiProcessor;
    protected final YaeltexButtonLedState[] slotColors;
    protected final YaeltexButtonLedState[] padColors;
    protected final YaeltexButtonLedState[] padColorsAlt;
    protected final NoteStep[] assignments;
    protected final PinnableCursorClip clip;
    protected final Set<Integer> addedSteps = new HashSet<>();
    protected final Set<Integer> modifiedSteps = new HashSet<>();
    protected final double gatePercent = 0.98;
    protected static final double STD_CHANCE = 0.5;
    private NoteStep copyNote;
    protected int selectedPadIndex = -1;
    protected int playingStep;
    
    public AbstractSequencerLayer(final Layers layers, final YaeltexMidiProcessor midiProcessor,
        final PinnableCursorClip clip, final int pads, final int steps, final int clipSlots) {
        super(layers, "SEQUENCER LAYER");
        this.clip = clip;
        this.midiProcessor = midiProcessor;
        positionHandler = new StepViewPosition(clip, steps, "YAELTEX");
        assignments = new NoteStep[steps];
        this.operatorNoteState = new NotesState(assignments, positionHandler, midiProcessor);
        slotColors = new YaeltexButtonLedState[clipSlots];
        padColors = new YaeltexButtonLedState[pads];
        padColorsAlt = new YaeltexButtonLedState[pads];
    }
    
    protected abstract int getVelocity();
    
    protected void prepareSlot(final int index, final ClipLauncherSlot slot) {
        slot.exists().markInterested();
        slot.isPlaying().markInterested();
        slot.hasContent().markInterested();
        slot.isPlaybackQueued().markInterested();
        slot.isStopQueued().markInterested();
        slot.color().addValueObserver((r, g, b) -> slotColors[index] = YaeltexButtonLedState.of(r, g, b));
    }
    
    protected static boolean isSettableSlot(final NoteStep note) {
        return note == null || note.state() == NoteStep.State.Empty || note.state() == NoteStep.State.NoteSustain;
    }
    
    protected void removeNote(final int index, final NoteStep note) {
        if (note != null && note.state() == NoteStep.State.NoteOn && !addedSteps.contains(index)) {
            if (!modifiedSteps.contains(index)) {
                this.clip.toggleStep(index, 0, getVelocity());
            } else {
                modifiedSteps.remove(index);
            }
        }
        addedSteps.remove(index);
    }
    
    protected void handleRandomAction(final int index, final NoteStep note) {
        if (note == null) {
            return;
        }
        if (note.chance() < 1.0) {
            note.setChance(1.0);
        } else {
            note.setChance(STD_CHANCE);
        }
    }
    
    protected void handleNoteCopyAction(final int index, final NoteStep note) {
        if (copyNote != null) {
            if (index == copyNote.x()) {
                return;
            }
            final int vel = (int) Math.round(copyNote.velocity() * 127);
            final double duration = copyNote.duration();
            operatorNoteState.registerChanges(index, copyNote);
            clip.setStep(index, 0, vel, duration);
        } else if (note != null && note.state() == NoteStep.State.NoteOn) {
            copyNote = note;
        }
    }
    
    protected void clearCopyNote() {
        copyNote = null;
    }
    
    protected void setStepLength(final int index) {
        positionHandler.setSteps(index);
    }
    
    protected YaeltexButtonLedState getStepLength(final int index) {
        if (index < positionHandler.getSteps()) {
            return YaeltexButtonLedState.BLUE;
        }
        return YaeltexButtonLedState.OFF;
    }
    
    
}
