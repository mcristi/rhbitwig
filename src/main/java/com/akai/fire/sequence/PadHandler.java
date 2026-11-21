package com.akai.fire.sequence;

import com.akai.fire.DrumSequencerExtension;
import com.akai.fire.ColorUtils;
import com.akai.fire.NoteAssign;
import com.akai.fire.ViewCursorControl;
import com.akai.fire.control.BiColorButton;
import com.akai.fire.control.RgbButton;
import com.akai.fire.display.DisplayInfo;
import com.akai.fire.display.DisplayTarget;
import com.akai.fire.display.OledDisplay.TextJustification;
import com.akai.fire.lights.BiColorLightState;
import com.akai.fire.lights.RgbLigthState;
import com.akai.fire.sequence.NoteAction.Type;
import com.akai.midimix.MidiMix;
import com.bitwig.extension.controller.api.*;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.values.BooleanValueObject;

import java.util.ArrayList;
import java.util.function.IntConsumer;
import java.util.List;

public class PadHandler implements MidiMix.FirePads {

    final DrumSequenceMode parent;
    private final PinnableCursorClip cursorClip;
    private final NoteInput noteInput;
    private final DrumPadBank padBank;
    private final PinnableCursorDevice drumDevice;

    private final NoteRepeatHandler noteRepeatHandler;

    private final List<PadContainer> pads = new ArrayList<>();

    RgbLigthState currentPadColor = RgbLigthState.PURPLE;

    PadContainer selectedPad;
    int selectedPadIndex = -1;
    private int drumScrollOffset;
    // told when the pad page changes (the MIDI Mix, which is tied to it)
    private IntConsumer pageListener;
    // keys whose pads flash until the given time, to show what the MIDI Mix covers
    private static final long FLASH_MS = 800;
    private int flashFirstKey = -1;
    private int flashCount = 0;
    private long flashUntil = -1;
    // by key, for all pads of the Drum Machine: has the pad something on it?
    private final boolean[] padExists = new boolean[128];
    // highest pad page position at which all 16 pads still are valid keys (0..127)
    private static final int MAX_SCROLL_POSITION = 128 - 16;
    // Bitwig's Drum Machine pages are 16 keys from C1 (36) up and down. That leaves a single row of
    // 4 below the lowest full page and 3 rows above the highest one; a page move never goes there.
    private static final int LOWEST_FULL_PAGE = 4;
    private static final int HIGHEST_FULL_PAGE = 100;
    // Moving the pad page makes Bitwig report the selected pad again at its new position. Until this
    // time that must not put up the "Selected Pad" screen, the page info is shown instead.
    private long padPageScrolledUntil = -1;

    private final BooleanValueObject[] playing = new BooleanValueObject[16];
    private final BooleanValueObject notePlayingActive = new BooleanValueObject();

    private final boolean[] drumTracker = new boolean[16];
    private final Integer[] notesToDrumTable = new Integer[128];
    private final int[] notesToPadsTable = new int[128];
    private final int[] padNotes = new int[16];
    private final DisplayTarget displayTarget;
    private final DisplayInfo padDisplayInfo;

    public PadHandler(final DrumSequencerExtension driver, final DrumSequenceMode parent, final Layer mainLayer,
                      final Layer muteLayer, final Layer soloLayer) {
        this.parent = parent;
        cursorClip = parent.getCursorClip();
        noteInput = driver.getNoteInput();
        for (int i = 0; i < padNotes.length; i++) {
            padNotes[i] = 0x36 + i;
        }
        final ViewCursorControl control = driver.getViewControl();

        padBank = control.getDrumPadBank();
        drumDevice = control.getPrimaryDevice();
        padBank.canScrollBackwards().markInterested();
        padBank.canScrollForwards().markInterested();
        padBank.scrollPosition().markInterested();
        final DrumPadBank allPads = control.getAllDrumPads();
        for (int i = 0; i < padExists.length; i++) {
            final int key = i;
            allPads.getItemAt(i).exists().addValueObserver(exists -> padExists[key] = exists);
        }

        setupPlaying(driver.getViewControl());

        displayTarget = new DisplayTarget(parent.getOled());

        final RgbButton[] rgbButtons = driver.getRgbButtons();
        for (int i = 0; i < 16; i++) {
            final RgbButton button = rgbButtons[i];
            final PadContainer pad = new PadContainer(this, i, control.getDrumPadBank().getItemAt(i), playing[i]);

            bindMain(button, mainLayer, pad);
            button.bind(muteLayer, () -> {
                pad.pad.mute().toggle();
                parent.notifyMuteAction();
            });
            button.bindLight(muteLayer, pad::mutingColors);
            button.bind(soloLayer, () -> {
                pad.pad.solo().toggle();
                parent.notifySoloAction();
            });
            button.bindLight(soloLayer, pad::soloingColors);
        }

        noteRepeatHandler = new NoteRepeatHandler(driver, parent);
        noteRepeatHandler.getNoteRepeatActive().addValueObserver(this::handleNoteRepeatChanged);

        notePlayingActive.addValueObserver(active -> {
            if (active) {
                applyScale();
            } else if (!notePlayingEnabled()) {
                disableNotePlaying();
            }
        });
        initButtons(mainLayer, driver);
        padDisplayInfo = new DisplayInfo() //
                .addLine("Selected Pad", 1, 0, TextJustification.CENTER) //
                .addLine(() -> selectedPad != null ? selectedPad.getName() : "", 2, 3, TextJustification.CENTER) //
                .create();
    }

    private void bindMain(final RgbButton button, final Layer mainLayer, final PadContainer pad) {
        pads.add(pad);
        button.bindPressed(mainLayer, p -> handlePadSelection(pad, p), pad::getColor);
    }

    private void initButtons(final Layer mainLayer, final DrumSequencerExtension driver) {
        final BiColorButton browerNrButton = driver.getButton(NoteAssign.BROWSER);
        browerNrButton.bindPressed(mainLayer, noteRepeatHandler::handlePressed, noteRepeatHandler::getLightState);
        final BiColorButton upNavButon = driver.getButton(NoteAssign.PATTERN_UP);
        upNavButon.markPressedInteressed();
        upNavButon.bindPressed(mainLayer, this::scrollForward, () -> canScrollUp(upNavButon));

        final BiColorButton downNavButon = driver.getButton(NoteAssign.PATTERN_DOWN);
        downNavButon.markPressedInteressed();
        downNavButon.bindPressed(mainLayer, this::scrollBackward, () -> canScrollDown(downNavButon));
    }

    private void handlePadSelection(final PadContainer pad, final boolean pressed) {
        if (pressed) {
            if (parent.isCopyHeld()) {
                doNotesPadCopy(pad);
            } else if (parent.isShiftHeld()) {
                pad.pad.color().set(ColorUtils.getColor(pad.pad));
            } else if (parent.isDeleteHeld()) {
                if (pad.index == selectedPadIndex) {
                    cursorClip.clearStepsAtY(0, 0);
                } else {
                    parent.registerPendingAction(new NoteAction(selectedPadIndex, pad.index, Type.CLEAR));
                    pad.pad.selectInEditor();
                }
            } else {
                // an empty pad of a Drum Machine gets a Komplete Kontrol to build on
                if (!pad.exists() && drumDevice.hasDrumPads().get()) {
                    pad.setUpEmptyPad(drumScrollOffset + pad.index);
                }
                pad.pad.selectInEditor();

                if (parent.isSelectHeld()) {
                    selectTrackAndPadInstrument(pad);
                }
            }
        }
    }

    /**
     * Selects the drum-rack track and the relevant instrument device
     * in the currently selected drum chain for the given pad.
     */
    private void selectTrackAndPadInstrument(final PadContainer pad) {
        cursorClip.getTrack().selectInEditor();
        pad.selectRelevantDevice();
    }

    /**
     * Replaces the notes of the pad now on display with the pattern.
     *
     * @param pattern    the notes with their values as they were read on the source pad. (Notes read
     *                   after the display has moved to another pad report 0 for length and velocity.)
     * @param copyParams false to copy only position, velocity and length
     */
    void executeCopy(final List<StepData> pattern, final boolean copyParams) {
        parent.clearPattern();
        // give Bitwig's reports about the target pad's old notes time to pass before the new notes
        // are expected
        parent.host.scheduleTask(() -> parent.writePattern(pattern, copyParams, () -> {
        }), 100);
    }

    void executeClear(final int origIndex) {
        cursorClip.clearStepsAtY(0, 0);
        if (origIndex != -1) {
            pads.get(origIndex).pad.selectInEditor();
        }
    }

    /**
     * The Pad has to be another pad then the currently selected pad. Copies notes
     * to that destination.
     *
     * @param pad destination pad of copy.
     */
    private void doNotesPadCopy(final PadContainer pad) {
        if (pad.index != selectedPadIndex) {
            // read the values now: once the display has moved to the other pad it is too late
            final List<StepData> pattern = parent.snapshotPattern();
            parent.registerPendingAction(new NoteAction(selectedPadIndex, pad.index, Type.COPY_PAD, pattern));
            cursorClip.scrollToKey(Math.min(127, drumScrollOffset + pad.index));
            pad.pad.selectInEditor();
        }
    }

    public void executePadSelection(final PadContainer pad) {
        currentPadColor = pad.getPadColor();
        selectedPad = pad;
        focusOnSelectedPad();
        selectedPadIndex = pad.getIndex();

        displayTarget.setFocusIndex(selectedPadIndex);
        displayTarget.setName(selectedPad.getName());

        // nothing else follows a pad selection that would clear the display, so time it out here
        if (System.currentTimeMillis() < padPageScrolledUntil) {
            // the selection did not change, only the page: see showPadPage
        } else if (parent.getOled().showInfo(padDisplayInfo)) {
            parent.getOled().clearScreenDelayed();
        }

        selectedPad.updateDisplay(displayTarget.getTypeIndex());
        final NoteAction pendingAction = parent.getPendingAction();
        if (pendingAction != null) {
            // An action is only valid for the selection it was registered for. If that selection
            // never came (e.g. an empty pad), drop it instead of running it on a later selection.
            parent.clearPendingAction();
            if (pendingAction.getDestPadIndex() == selectedPadIndex && !pendingAction.isExpired()) {
                if (pendingAction.getType() == Type.CLEAR) {
                    executeClear(pendingAction.getSrcPadIndex());
                } else if (pendingAction.getType() == Type.COPY_PAD) {
                    executeCopy(pendingAction.getCopyNotes(), !parent.isShiftHeld());
                }
            }
        }
    }

    public void focusOnSelectedPad() {
        final int padIndex = selectedPad != null ? selectedPad.index : 0;
        cursorClip.scrollToKey(Math.min(127, drumScrollOffset + padIndex));
    }

    public String getPadInfo() {
        if (selectedPad != null) {
            return selectedPad.getName();
        }
        return "";
    }

    public RgbLigthState getCurrentPadColor() {
        return currentPadColor;
    }

    private void setupPlaying(final ViewCursorControl control) {
        final DrumPadBank drumPadBank = control.getDrumPadBank();
        final CursorTrack cursorTrack = control.getCursorTrack();
        for (int i = 0; i < notesToDrumTable.length; i++) {
            notesToDrumTable[i] = -1;
            notesToPadsTable[i] = -1;
        }
        noteInput.setKeyTranslationTable(notesToDrumTable);
        drumPadBank.scrollPosition().addValueObserver(offset -> {
            drumScrollOffset = offset;
            focusOnSelectedPad();
            applyScale();
            if (pageListener != null) {
                pageListener.accept(offset);
            }
        });
        for (int i = 0; i < 16; i++) {
            playing[i] = new BooleanValueObject();
            playing[i].set(false);
        }
        cursorTrack.playingNotes().addValueObserver(this::handleNotes);
    }

    private void handleNotes(final PlayingNote[] notes) {
        if (!parent.isActive()) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            drumTracker[i] = false;
        }
        for (final PlayingNote playingNote : notes) {
            final int padIndex = notesToPadsTable[playingNote.pitch()];
            if (padIndex != -1) {
                playing[padIndex].set(true);
                drumTracker[padIndex] = true;
            }
        }
        for (int i = 0; i < 16; i++) {
            if (!drumTracker[i]) {
                playing[i].set(false);
            }
        }
    }

    boolean notePlayingEnabled() {
        return notePlayingActive.get() || noteRepeatHandler.getNoteRepeatActive().get();
    }

    private void handleNoteRepeatChanged(final boolean nrActive) {
        if (nrActive) {
            noteRepeatHandler.activate();
            applyScale();
        } else {
            noteRepeatHandler.deactivate();
            if (!notePlayingEnabled()) {
                disableNotePlaying();
            }
        }
    }

    void disableNotePlaying() {
        if (!parent.isActive()) {
            return;
        }
        for (int i = 0; i < 128; i++) {
            notesToDrumTable[i] = -1;
        }
        noteInput.setKeyTranslationTable(notesToDrumTable);
    }

    void applyScale() {
        if (!parent.isActive()) {
            return;
        }
        for (int i = 0; i < 128; i++) {
            notesToPadsTable[i] = -1;
        }
        for (int i = 0; i < 16; i++) {
            final int padnote = padNotes[i];
            final int noteToPadIndex = drumScrollOffset + i;
            if (noteToPadIndex < 128) {
                notesToDrumTable[padnote] = noteToPadIndex;
                notesToPadsTable[noteToPadIndex] = i;
            }
        }
        if (notePlayingEnabled()) {
            noteInput.setKeyTranslationTable(notesToDrumTable);
        }
    }

    public void handleMainEncoder(final int inc) {
        noteRepeatHandler.handleMainEncoder(inc);
    }

    public void handleMainEncoderPress(final boolean pressed) {
        if (pressed) {
            noteRepeatHandler.handleMainEncoderPress();
        }
    }

    /** Note Transpose of the selected pad back to where a sample plays at its original pitch. */
    public void resetSemisToOriginalPitch() {
        if (selectedPad != null) {
            selectedPad.setSemis(PadContainer.originalPitchSemis(drumScrollOffset + selectedPad.index));
        }
    }

    /** Puts remote control 1, 2 or 5 of the selected pad's first device back to its default. */
    public void resetMacroValue(final int macro) {
        if (selectedPad != null) {
            selectedPad.resetMacroValue(macro);
        }
    }

    /**
     * @param dir +1 for up, -1 for down
     * @return the position a press of Pattern up / down leads to, or -1 if it must not move: a page
     *         move (16) stays on full 4x4 pages, a move with Shift (4) may use the whole key range
     */
    private int scrollTarget(final int dir) {
        final boolean fine = parent.isShiftHeld();
        final int target = padBank.scrollPosition().get() + dir * (fine ? 4 : 16);
        final int min = fine ? 0 : LOWEST_FULL_PAGE;
        final int max = fine ? MAX_SCROLL_POSITION : HIGHEST_FULL_PAGE;
        return target >= min && target <= max ? target : -1;
    }

    public void setPageListener(final IntConsumer listener) {
        pageListener = listener;
    }

    @Override
    public int position() {
        return drumScrollOffset;
    }

    @Override
    public boolean scrollPage(final int dir) {
        final int target = padBank.scrollPosition().get() + dir * 16;
        if (target < LOWEST_FULL_PAGE || target > HIGHEST_FULL_PAGE) {
            return false;
        }
        // as for the Fire's own page buttons: no "Selected Pad" screen for the pad moving along
        padPageScrolledUntil = System.currentTimeMillis() + 500;
        padBank.scrollPosition().set(target);
        return true;
    }

    @Override
    public void flash(final int firstKey, final int count) {
        flashFirstKey = firstKey;
        flashCount = count;
        flashUntil = System.currentTimeMillis() + FLASH_MS;
    }

    /** @return true while the pad at the given position should be shown white as part of a flash */
    boolean isFlashLit(final int padIndex) {
        final int key = drumScrollOffset + padIndex;
        return System.currentTimeMillis() < flashUntil && key >= flashFirstKey && key < flashFirstKey + flashCount
                && !parent.isSourceBlinkDark();
    }

    /** Lit if the pads that page up would bring in include at least one with a device on it. */
    private BiColorLightState canScrollUp(final BiColorButton button) {
        final int position = padBank.scrollPosition().get();
        final int target = scrollTarget(1);
        if (target != -1 && hasPads(position + 16, target + 15)) {
            return button.isPressed() ? BiColorLightState.FULL : BiColorLightState.HALF;
        }
        return BiColorLightState.OFF;
    }

    /** Lit if the pads that page down would bring in include at least one with a device on it. */
    private BiColorLightState canScrollDown(final BiColorButton button) {
        final int position = padBank.scrollPosition().get();
        final int target = scrollTarget(-1);
        if (target != -1 && hasPads(target, position - 1)) {
            return button.isPressed() ? BiColorLightState.FULL : BiColorLightState.HALF;
        }
        return BiColorLightState.OFF;
    }

    /** @return true if a pad with something on it lies on one of the keys from..to (inclusive) */
    private boolean hasPads(final int from, final int to) {
        for (int key = Math.max(0, from); key <= Math.min(127, to); key++) {
            if (padExists[key]) {
                return true;
            }
        }
        return false;
    }

    private void scrollForward(final boolean pressed) {
        if (!pressed) {
            parent.getOled().clearScreenDelayed();
            return;
        }
        final int target = scrollTarget(1);
        if (target != -1) {
            padBank.scrollPosition().set(target);
        }
        showPadPage();
    }

    private void scrollBackward(final boolean pressed) {
        if (!pressed) {
            parent.getOled().clearScreenDelayed();
            return;
        }
        final int target = scrollTarget(-1);
        if (target != -1) {
            padBank.scrollPosition().set(target);
        }
        showPadPage();
    }

    /** Shows which keys the 16 pads cover now, and how many of them have something on them. */
    private void showPadPage() {
        padPageScrolledUntil = System.currentTimeMillis() + 500;
        // a moment later, when Bitwig has reported the new position and its pads
        parent.host.scheduleTask(() -> {
            final String range = PadContainer.keyName(drumScrollOffset) + " - "
                    + PadContainer.keyName(Math.min(127, drumScrollOffset + 15));
            final long used = pads.stream().filter(PadContainer::exists).count();
            parent.getOled().paramInfo("Pad Page", range, used + " of 16 pads used");
        }, 100);
    }

    public DisplayTarget getDiplayTarget() {
        return displayTarget;
    }

    public void activateView(final int typeIndex, final String paramName) {
        String realParamName = paramName;

        if (paramName == null) {
            // Try to fetch the real parameter name from the active remote controls page.
            CursorRemoteControlsPage remotePage = parent.getActiveRemoteControlsPage();

            // For macro parameters, our typeIndex starts at 4.
            int offset = 4;
            int remoteParamIndex = typeIndex - offset;

            // no page until a pad has been selected (e.g. track without a Drum Machine)
            if (remotePage != null && remoteParamIndex >= 0 && remoteParamIndex < remotePage.getParameterCount()) {
                Parameter parameter = remotePage.getParameter(remoteParamIndex);
                if (parameter != null) {
                    realParamName = parameter.name().get();
                }
            }
        }

        displayTarget.setTypeIndex(typeIndex, realParamName);
        displayTarget.activate();
    }

    public void deactivateView() {
        displayTarget.deactivate();
    }

    public void modifyValue(final int typeIndex, final int inc) {
        if (selectedPad == null) {
            return;
        }
        selectedPad.modifyValue(typeIndex, inc, parent.isShiftHeld());
    }

    public void setTuneValue(final int inc) {
        if (selectedPad == null) {
            return;
        }
        double value = inc * (1 / 24.0);
        selectedPad.setMacro1Value(value);
    }

    public double getTuneValue() {
        if (selectedPad == null) {
            return 0.0;
        }
        double value = selectedPad.getMacro1Value();
        return Math.round((float)(value - 0.5) * 24);
    }

    public void setSemisValue(final int inc) {
        if (selectedPad == null) {
            return;
        }
        selectedPad.increaseMacro2Value(inc);
    }

    public double getSemisValue() {
        if (selectedPad == null) {
            return 0;
        }
        double value = selectedPad.getMacro2Value();
        return Math.round((value - 0.5) * 96);
    }

    public void setPitchValue(final int inc) {
        if (selectedPad == null) {
            return;
        }
        double value = inc * (1 / 72.0);
        selectedPad.setMacro5Value(value);
    }

    public double getPitchValue() {
        if (selectedPad == null) {
            return 0.0;
        }
        double value = selectedPad.getMacro5Value();
        return Math.round((float)(value - 0.5) * 72);
    }


    public void setSampleValue(final int inc) {
        if (selectedPad == null) {
            return;
        }
        double value = inc * (1 / 127.0);
        selectedPad.setMacro8Value(value);
    }

    public double getSampleValue() {
        if (selectedPad == null) {
            return 0;
        }
        double sampleValue = selectedPad.getMacro8Value() * 127;
        return Math.round(sampleValue * 10.0) / 10.0;
    }

    public void bindPadParameters(final Layer layer) {
        for (final PadContainer pad : pads) {
            pad.bindParameters(layer);
        }
    }

    public void bindPadMacros(final Layer layer) {
        for (final PadContainer pad : pads) {
            pad.bindMacros(layer);
        }
    }

    public void bindPadMacros2(final Layer layer) {
        for (final PadContainer pad : pads) {
            pad.bindMacros2(layer);
        }
    }

    public void updateDisplay(final int index) {
        if (selectedPad != null) {
            selectedPad.updateDisplay(index);
        }

    }

    public NoteRepeatHandler getNoteRepeaterHandler() {
        return noteRepeatHandler;
    }
}
