package com.akai.fire.sequence;

import com.akai.fire.DrumSequencerExtension;
import com.akai.fire.NoteAssign;
import com.akai.fire.control.BiColorButton;
import com.akai.fire.control.RgbButton;
import com.akai.fire.control.TouchEncoder;
import com.akai.fire.display.DisplayInfo;
import com.akai.fire.display.OledDisplay;
import com.akai.fire.display.OledDisplay.TextJustification;
import com.akai.fire.lights.BiColorLightState;
import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.DrumPadBank;
import com.bitwig.extension.controller.api.MultiStateHardwareLight;
import com.bitwig.extension.controller.api.NoteOccurrence;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extension.controller.api.NoteStep.State;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.values.BooleanValueObject;
import com.bitwig.extensions.framework.values.StepViewPosition;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DrumSequenceMode extends Layer {

    private final IntSetValue heldSteps = new IntSetValue();
    private final Set<Integer> addedSteps = new HashSet<>();
    // true once an encoder was touched or turned during the current step hold (from the first
    // step pressed until all are released): those steps are being edited and stay on release
    private boolean encoderUsedWhileHolding = false;
    // step -> what to do with the note that is about to appear there
    private final HashMap<Integer, Consumer<NoteStep>> expectedNoteChanges = new HashMap<>();
    // runs once all expected notes of a written pattern have arrived, see writePattern
    private Runnable afterPatternWritten;
    private int patternWriteId = 0;

    private final NoteStep[] assignments = new NoteStep[32];

    private final OledDisplay oled;

    private final Layer mainLayer;
    private Layer currentLayer;
    private final Layer muteLayer;
    private final Layer soloLayer;
    private final SequencEncoderHandler encoderLayer;

    private final CursorTrack cursorTrack;
    private final PinnableCursorClip cursorClip;

    private final StepViewPosition positionHandler;
    private final ResolutionHander resolutionHandler;
    private final SeqClipHandler clipHandler;
    private final RecurrenceEditor recurrenceEditor;
    private final StepValueBar stepValueBar;
    private final PadHandler padHandler;

    private final BooleanValueObject muteMode = new BooleanValueObject();
    private final BooleanValueObject soloMode = new BooleanValueObject();
    private final BooleanValueObject selectHeld = new BooleanValueObject();
    private final BooleanValueObject copyHeld = new BooleanValueObject();
    private final BooleanValueObject deleteHeld = new BooleanValueObject();
    private final BooleanValueObject fixedLengthHeld = new BooleanValueObject();
    private final BooleanValueObject shiftActive = new BooleanValueObject();
    private final BooleanValueObject clipLaunchModeQuant = new BooleanValueObject();
    private final BooleanValueObject lengthDisplay = new BooleanValueObject();

    private final BooleanValueObject muteActionsTaken = new BooleanValueObject();
    private final BooleanValueObject soloActionsTaken = new BooleanValueObject();

    private int playingStep;
    // playing step counted from the clip start; playingStep is relative to the visible page
    private int clipPlayingStep = -1;
    // a grid button press was used for paging, so its release must not move the pattern
    private boolean pageScrolled = false;
    private final double gatePercent = 0.98;
    private final AccentHandler accentHandler;
    private NoteAction pendingAction;
    private NoteStep copyNote = null;
    private int blinkState;

    private CursorRemoteControlsPage activeRemoteControlsPage;

    public ControllerHost host;

    public DrumSequenceMode(final DrumSequencerExtension driver) {
        super(driver.getLayers(), "DRUM_SEQUENCE_LAYER");
        oled = driver.getOled();
        mainLayer = new Layer(getLayers(), getName() + "_MAIN");
        muteLayer = new Layer(getLayers(), getName() + "_MUTE");
        soloLayer = new Layer(getLayers(), getName() + "_SOLO");

        currentLayer = mainLayer;
        accentHandler = new AccentHandler(this);
        resolutionHandler = new ResolutionHander(this);

        cursorTrack = driver.getViewControl().getCursorTrack();
        cursorTrack.name().markInterested();
        cursorTrack.isPinned().markInterested();
        cursorClip = cursorTrack.createLauncherCursorClip("SQClip", "SQClip", 32, 1);

        cursorClip.addNoteStepObserver(this::handleNoteStep);
        cursorClip.playingStep().addValueObserver(this::handlePlayingStep);
        cursorClip.getLoopLength().markInterested();
        cursorClip.isPinned().markInterested();
        positionHandler = new StepViewPosition(cursorClip, 32, "AKAI");

        padHandler = new PadHandler(driver, this, mainLayer, muteLayer, soloLayer);
        clipHandler = new SeqClipHandler(driver, this, mainLayer);
        recurrenceEditor = new RecurrenceEditor(driver, this);
        stepValueBar = new StepValueBar(driver, this);

        initSequenceSection(driver);
        initModeButtons(driver);
        initButtonBehaviour(driver);
        encoderLayer = new SequencEncoderHandler(this, driver, padHandler);

        muteMode.addValueObserver(active -> {
            if (active) {
                muteLayer.activate();
            } else {
                muteLayer.deactivate();
            }
        });

        soloMode.addValueObserver(active -> {
            if (active) {
                soloLayer.activate();
            } else {
                soloLayer.deactivate();
            }
        });
        copyHeld.addValueObserver(held -> {
            if (!held && copyNote != null) {
                copyNote = null;
            }
        });
        selectHeld.addValueObserver(held -> {
            if (!held) {
                heldSteps.removeAll();
                encoderLayer.switchToPadEncoders();
            }
        });

        final TouchEncoder mainEncoder = driver.getMainEncoder();
        mainEncoder.setStepSize(0.4);
        mainEncoder.bindEncoder(mainLayer, this::handleMainEncoder);
        mainEncoder.bindTouched(mainLayer, this::handeMainEncoderPress);

        host = driver.host;
    }

    private void initModeButtons(final DrumSequencerExtension driver) {
        final MultiStateHardwareLight[] stateLights = driver.getStateLights();
        bindEditButton(driver.getButton(NoteAssign.MUTE_1), "Select", selectHeld, stateLights[0], null, null, BiColorLightState.OFF, BiColorLightState.AMBER_FULL, BiColorLightState.AMBER_FULL);
        bindEditButton(driver.getButton(NoteAssign.MUTE_2), "Copy", copyHeld, stateLights[1], null, null, BiColorLightState.AMBER_HALF, BiColorLightState.AMBER_FULL, BiColorLightState.AMBER_FULL);
        bindEditButton(driver.getButton(NoteAssign.MUTE_3), "Delete/Reset", deleteHeld, stateLights[2], null, null, BiColorLightState.RED_HALF, BiColorLightState.RED_FULL, BiColorLightState.RED_FULL);
        bindEditButton(driver.getButton(NoteAssign.MUTE_4), "Last Step", fixedLengthHeld, stateLights[3], null, null, BiColorLightState.OFF, BiColorLightState.AMBER_FULL, BiColorLightState.RED_FULL);

        // In the mute and solo layers the Select button resets: unmute all / unsolo all. The LED next
        // to it tells if there is anything to reset: red = some pad is muted (soloed), green = none.
        final BiColorButton resetButton = driver.getButton(NoteAssign.MUTE_1);
        final DrumPadBank padBank = driver.getViewControl().getDrumPadBank();
        padBank.hasMutedPads().markInterested();
        padBank.hasSoloedPads().markInterested();
        resetButton.bindPressed(muteLayer, pressed -> {
            if (pressed) {
                padBank.clearMutedPads();
                oled.paramInfo("Mute", "All unmuted", cursorTrack.name().get());
            } else {
                oled.clearScreenDelayed();
            }
        }, () -> resetButton.isPressed() ? BiColorLightState.GREEN_FULL : BiColorLightState.OFF);
        muteLayer.bindLightState(
                () -> padBank.hasMutedPads().get() ? BiColorLightState.TRACK_RED : BiColorLightState.TRACK_GREEN,
                stateLights[0]);
        resetButton.bindPressed(soloLayer, pressed -> {
            if (pressed) {
                padBank.clearSoloedPads();
                oled.paramInfo("Solo", "All unsoloed", cursorTrack.name().get());
            } else {
                oled.clearScreenDelayed();
            }
        }, () -> resetButton.isPressed() ? BiColorLightState.GREEN_FULL : BiColorLightState.OFF);
        soloLayer.bindLightState(
                () -> padBank.hasSoloedPads().get() ? BiColorLightState.TRACK_RED : BiColorLightState.TRACK_GREEN,
                stateLights[0]);
    }

    private void initButtonBehaviour(final DrumSequencerExtension driver) {

        final BiColorButton accentButton = driver.getButton(NoteAssign.STEP_SEQ);
        accentButton.bindPressed(mainLayer, accentHandler::handlePressed, accentHandler::getLightState);

        final BiColorButton shiftButton = driver.getButton(NoteAssign.SHIFT);
        shiftButton.bindPressed(mainLayer, pressed -> {
            if (pressed) {
                shiftActive.set(true);
                oled.detailInfo("Shift", "+ pad: toggle colors\n+ mode: alt mode\n+ select: grid size\n+ knob: fine adjust\n+ mute/solo: pin\n+ grid </>: step page\nsteps show operators");
            } else {
                shiftActive.set(false);
                oled.clearScreenDelayed();
            }
        }, BiColorLightState.GREEN_HALF, BiColorLightState.OFF);

        final BiColorButton clipLaunchModeButton = driver.getButton(NoteAssign.NOTE);
        clipLaunchModeButton.bindToggle(mainLayer, clipLaunchModeQuant, BiColorLightState.AMBER_FULL,
                BiColorLightState.AMBER_HALF, oled,
                new DisplayInfo().addLine("Clip Legato", 2, 0, TextJustification.CENTER)//
                        .addLine(() -> clipLaunchModeQuant.get() ? "with quant" : "immediate", 2, 3,
                                TextJustification.CENTER)//
                        .create());

        final BiColorButton muteModeButton = driver.getButton(NoteAssign.DRUM);
        muteModeButton.bindPressed(mainLayer, pressed -> {
            if (pressed) {
                final FunctionInfo info = FunctionInfo.INFO2.get(NoteAssign.MUTE_1);
                oled.functionInfo(getPadInfo(), info.getName(false), info.getDetail());

                muteMode.set(true);
            } else {
                if (!getShiftActive().get()) {
                    muteMode.set(false);
                }

                oled.clearScreenDelayed();
            }
        }, () -> muteMode.get() ? BiColorLightState.RED_FULL : BiColorLightState.OFF);


        final BiColorButton soloModeButton = driver.getButton(NoteAssign.PERFORM);
        soloModeButton.bindPressed(mainLayer, pressed -> {
            if (pressed) {
                final FunctionInfo info = FunctionInfo.INFO2.get(NoteAssign.MUTE_2);
                oled.functionInfo(getPadInfo(), info.getName(false), info.getDetail());

                soloMode.set(true);
            } else {
                if (!getShiftActive().get()) {
                    soloMode.set(false);
                }

                oled.clearScreenDelayed();
            }
        }, () -> soloMode.get() ? BiColorLightState.AMBER_FULL : BiColorLightState.OFF);

        final BiColorButton pinButton = driver.getButton(NoteAssign.ALT);
        pinButton.bindPressed(mainLayer, this::handleClipPinning, this::getPinnedState);

        final BiColorButton shiftLeftButton = driver.getButton(NoteAssign.BANK_L);
        shiftLeftButton.bindPressed(mainLayer, p -> handleGridButton(p, -1),
                () -> gridButtonState(shiftLeftButton, positionHandler.canScrollLeft()));
        final BiColorButton shiftRightButton = driver.getButton(NoteAssign.BANK_R);
        shiftRightButton.bindPressed(mainLayer, p -> handleGridButton(p, 1),
                () -> gridButtonState(shiftRightButton, positionHandler.canScrollRight()));
    }

    private void initSequenceSection(final DrumSequencerExtension driver) {
        final RgbButton[] rgbButtons = driver.getRgbButtons();
        for (int i = 0; i < 32; i++) {
            final RgbButton button = rgbButtons[i + 32];
            final int index = i;
            button.bindPressed(mainLayer, p -> handleSeqSelection(index, p), () -> stepState(index));
        }
    }

    private void handleSeqSelection(final int index, final boolean pressed) {
        final NoteStep note = assignments[index];
        if (!pressed) {
            if (selectHeld.get()) {
                addedSteps.remove(index);
                return;
            }

            heldSteps.remove(index);
            if (copyHeld.get() || fixedLengthHeld.get()) {
                // do nothing
            } else if (note != null && note.state() == State.NoteOn && !addedSteps.contains(index)) {
                if (!encoderUsedWhileHolding) {
                    cursorClip.toggleStep(index, 0, accentHandler.getCurrenVel());
                }
            }
            addedSteps.remove(index);

            // keep the step encoders while other steps are still held
            if (heldSteps.isEmpty()) {
                encoderLayer.switchToPadEncoders();
            }
        } else {
            if (heldSteps.isEmpty()) {
                // a new hold starts; an encoder that is already touched counts for it
                encoderUsedWhileHolding = encoderLayer.isAnyEncoderTouched();
            }
            heldSteps.add(index);

            if (fixedLengthHeld.get()) {
                stepActionFixedLength(index);
            } else if (copyHeld.get()) {
                handleNoteCopyAction(index, note);
            } else if (selectHeld.get()) {
                // do nothing
            } else {
                if (note == null || note.state() == State.Empty || note.state() == State.NoteSustain) {
                    cursorClip.setStep(index, 0, accentHandler.getCurrenVel(),
                            positionHandler.getGridResolution() * gatePercent);
                    addedSteps.add(index);
                }
            }

            encoderLayer.switchToStepsEncoders();
        }
    }

    private void handleNoteCopyAction(final int index, final NoteStep note) {
        if (copyNote != null) {
            if (index == copyNote.x()) {
                return;
            }
            final int vel = (int) Math.round(copyNote.velocity() * 127);
            final double duration = copyNote.duration();
            final NoteStep source = copyNote;
            expectedNoteChanges.put(index, dest -> applyValues(dest, source));
            cursorClip.setStep(index, 0, vel, duration);
        } else if (note != null && note.state() == State.NoteOn) {
            copyNote = note;
        }
    }

    private RgbLigthState stepState(final int index) {
        final int steps = positionHandler.getAvailableSteps();
        if (index < steps) {
            final State state = assignments[index] == null ? State.Empty : assignments[index].state();

            if (isShiftHeld()) {
                // operator view: only what has an operator stands out, no playhead, no blinking
                return state == State.NoteOn ? operatorState(assignments[index]) : RgbLigthState.OFF;
            }

            if (state == State.Empty) {
                return emptyNoteState(index);
            } else if (state == State.NoteSustain) {
                if (lengthDisplay.get()) {
                    if (index == playingStep) {
                        return padHandler.getCurrentPadColor().getBrightend();
                    }
                    return padHandler.getCurrentPadColor().getVeryDimmed();
                }
                return emptyNoteState(index);
            }

            if ((copyNote != null && copyNote.x() == index) ||
                (isSelectHeld() && heldSteps.contains(index))
            ) {
                if (isSourceBlinkDark()) {
                    return RgbLigthState.GRAY_1;
                }
                return padHandler.getCurrentPadColor();
            }
            if (index == playingStep) {
                return padHandler.getCurrentPadColor().getBrightend();
            }
            return padHandler.getCurrentPadColor().getForVelocity(
                    (int) Math.round(assignments[index].velocity() * 127), accentHandler.getStandardVel(),
                    accentHandler.getAccentedVel());

        }
        return RgbLigthState.OFF;
    }

    /**
     * The step in the operator view (Shift held): the color of its operator, in the colors of the
     * encoders that edit them: orange = chance, yellow = repeats, green = recurrence, red = an "if"
     * condition (e.g. Fill on), white = more than one. A note without operators is dim grey.
     */
    private RgbLigthState operatorState(final NoteStep note) {
        RgbLigthState color = RgbLigthState.GRAY_1;
        int operators = 0;
        if (note.isChanceEnabled() && note.chance() < 0.999) {
            color = RgbLigthState.ORANGE;
            operators++;
        }
        if (note.repeatCount() != 0) {
            color = RgbLigthState.YELLOW;
            operators++;
        }
        if (note.recurrenceLength() > 1) {
            color = RgbLigthState.GREEN;
            operators++;
        }
        if (note.occurrence() != NoteOccurrence.ALWAYS) {
            color = RgbLigthState.RED;
            operators++;
        }
        return operators > 1 ? RgbLigthState.WHITE : color;
    }

    private RgbLigthState emptyNoteState(final int index) {
        if (index == playingStep) {
            return RgbLigthState.WHITE;
        }
        if (index / 4 % 2 == 0) {
            return RgbLigthState.GRAY_1;
        } else {
            return RgbLigthState.GRAY_2;
        }
    }

    /** Grid left/right: move the notes of the visible page. With Shift: change the step page. */
    private void handleGridButton(final boolean pressed, final int dir) {
        if (pressed) {
            pageScrolled = shiftActive.get();
            if (pageScrolled) {
                scrollPage(dir);
            }
        } else if (pageScrolled) {
            pageScrolled = false;
            oled.clearScreenDelayed();
        } else {
            movePattern(dir);
        }
    }

    /** With Shift held the grid buttons show whether there is a page in that direction. */
    private BiColorLightState gridButtonState(final BiColorButton button, final BooleanValueObject canScroll) {
        if (shiftActive.get()) {
            return canScroll.get() ? BiColorLightState.HALF : BiColorLightState.OFF;
        }
        return button.isPressed() ? BiColorLightState.HALF : BiColorLightState.OFF;
    }

    private void scrollPage(final int dir) {
        // held steps are indices into the current page, don't pull it away under them
        if (heldSteps.isEmpty()) {
            if (dir < 0) {
                positionHandler.scrollLeft();
            } else {
                positionHandler.scrollRight();
            }
            updatePlayingStep();
        }
        oled.valueInfo("Step Page", (positionHandler.getCurrentPage() + 1) + "/" + positionHandler.getPages());
    }

    private void movePattern(final int dir) {
        final List<NoteStep> notes = getOnNotes();
        final int availableSteps = Math.min(assignments.length, positionHandler.getAvailableSteps());
        expectedNoteChanges.clear();
        // only remove what is rewritten below, notes on other pages stay untouched
        for (final NoteStep noteStep : notes) {
            cursorClip.clearStep(0, noteStep.x(), 0);
        }

        for (final NoteStep noteStep : notes) {
            int pos = noteStep.x() + dir;
            if (pos < 0) {
                pos = availableSteps - 1;
            } else if (pos >= availableSteps) {
                pos = 0;
            }
            expectedNoteChanges.put(pos, dest -> applyValues(dest, noteStep));
            cursorClip.setStep(pos, 0, (int) Math.round(noteStep.velocity() * 127), noteStep.duration());
        }
    }

    private BiColorLightState getPinnedState() {
        return cursorTrack.isPinned().get() ? BiColorLightState.HALF : BiColorLightState.OFF;
    }

    private void handleClipPinning(final boolean pressed) {
        if (pressed) {
            cursorTrack.isPinned().toggle();
            oled.paramInfo((cursorTrack.isPinned().get() ? "UNPIN" : "PIN") + " Track", "TR:" + cursorTrack.name().get());
        } else {
            oled.clearScreenDelayed();
        }
    }

    private void handleMainEncoder(final int inc) {
        if (accentHandler.isHolding()) {
            accentHandler.handleMainEncoder(inc);
        } else if (getShiftActive().get()) {
            resolutionHandler.handleMainEncoder(inc);
        } else {
            padHandler.handleMainEncoder(inc);
        }
    }

    private void handeMainEncoderPress(final boolean press) {
        if (accentHandler.isHolding()) {
            accentHandler.handeMainEncoderPress(press);
        } else if (getShiftActive().get()) {
            resolutionHandler.handeMainEncoderPress(press);
        } else {
            padHandler.handleMainEncoderPress(press);
        }
    }

    public BooleanValueObject getShiftActive() {
        return shiftActive;
    }

    public BooleanValueObject getDeleteHeld() {
        return deleteHeld;
    }

    public void notifyBlink(final int blinkTicks) {
        blinkState = blinkTicks;
        clipHandler.notifyBlink(blinkTicks);
    }

    public OledDisplay getOled() {
        return oled;
    }

    private void bindEditButton(final BiColorButton button, final String name, final BooleanValueObject value,
                                final MultiStateHardwareLight stateLight, final BooleanValueObject altValue,
                                final BooleanValueObject altActionHappenedFlag, final BiColorLightState color, final BiColorLightState pressedColor, final BiColorLightState shiftPressedColor) {
        if (altValue == null) {
            final FunctionInfo info1 = FunctionInfo.INFO1.get(button.getNoteAssign());
            button.bind(mainLayer, value, BiColorLightState.GREEN_FULL, BiColorLightState.OFF);
            mainLayer.bindLightState(() -> BiColorLightState.AMBER_HALF, stateLight);
            value.addValueObserver(active -> handleEditValueChanged(button, active, info1));
            mainLayer.bindLightState(
                    () -> button.isPressed() ? (isShiftHeld() ? shiftPressedColor : pressedColor) : color, stateLight);
        } else {
            final BooleanValueObject alternateFunctionActive = new BooleanValueObject();
            final FunctionInfo info1 = FunctionInfo.INFO1.get(button.getNoteAssign());
            value.addValueObserver(active -> handleEditValueChanged(button, active, info1));
            final FunctionInfo info2 = FunctionInfo.INFO2.get(button.getNoteAssign());
            altValue.addValueObserver(active -> handleEditValueChanged(button, active, info2));
            button.bindPressed(mainLayer,
                    pressed -> handleModeButtonWithAlternatePressed(value, altValue, alternateFunctionActive,
                            altActionHappenedFlag, info1, info2, pressed),  //
                    () -> button.isPressed() ? BiColorLightState.GREEN_FULL : BiColorLightState.OFF);
            mainLayer.bindLightState(() -> {
                final boolean active = button.isPressed() && !getShiftActive().get();
                if (alternateFunctionActive.get()) {
                    return active ? BiColorLightState.RED_FULL : BiColorLightState.RED_HALF;
                }
                return active ? BiColorLightState.AMBER_FULL : BiColorLightState.AMBER_HALF;
            }, stateLight);
        }
    }

    private void handleModeButtonWithAlternatePressed(final BooleanValueObject mainValue,
                                                      final BooleanValueObject altValue,
                                                      final BooleanValueObject alternateFunctionActive,
                                                      final BooleanValueObject actionTakenFlag,
                                                      final FunctionInfo info1, final FunctionInfo info2,
                                                      final Boolean pressed) {
        if (pressed) {
            if (getShiftActive().get()) {
                alternateFunctionActive.set(!alternateFunctionActive.get());
            } else {
                alternateFunctionActive.set(false);
            }
            boolean isAlternateFunctionActive = alternateFunctionActive.get();

            mainValue.set(!alternateFunctionActive.get());
            altValue.set(alternateFunctionActive.get());

            actionTakenFlag.set(true);
            oled.functionInfo(
                getPadInfo(),
                isAlternateFunctionActive ? info2.getName(false) : info1.getName(false),
                isAlternateFunctionActive ? info2.getDetail() : info1.getDetail()
            );
        }

        if (!pressed) {
            mainValue.set(false);
            if (!alternateFunctionActive.get()) {
                altValue.set(false);
            }

            actionTakenFlag.set(false);
            oled.clearScreenDelayed();
        }
    }

    public String getPadInfo() {
        return padHandler.getPadInfo();
    }

    private void handleEditValueChanged(final BiColorButton button, final boolean active, final FunctionInfo info) {
        if (active) {
            if (padHandler.notePlayingEnabled()) {
                padHandler.disableNotePlaying();
            }
            oled.functionInfo(getPadInfo(), info.getName(shiftActive.get()), info.getDetail());
        } else {
            oled.clearScreenDelayed();
            if (padHandler.notePlayingEnabled()) {
                padHandler.applyScale();
            }
        }
    }

    double getGridResolution() {
        return positionHandler.getGridResolution();
    }

    String getDetails(final List<NoteStep> heldNotes) {
        return getPadInfo() + " <" + heldNotes.size() + ">";
    }

    /** An encoder was touched or turned: steps held right now must survive their release. */
    public void notifyEncoderUsed() {
        if (!heldSteps.isEmpty()) {
            encoderUsedWhileHolding = true;
        }
    }

    List<NoteStep> getHeldNotes() {
        return heldSteps.stream()//
                .map(idx -> assignments[idx])//
                .filter(ns -> ns != null && ns.state() == State.NoteOn) //
                .collect(Collectors.toList());
    }

    List<NoteStep> getOnNotes() {
        return Arrays.stream(assignments)
                .filter(ns -> ns != null && ns.state() == State.NoteOn)
                .collect(Collectors.toList());
    }

    public void registerPendingAction(final NoteAction action) {
        pendingAction = action;
    }

    public NoteAction getPendingAction() {
        return pendingAction;
    }

    public void clearPendingAction() {
        pendingAction = null;
    }

    private void stepActionFixedLength(final int index) {
        final double newLen = positionHandler.lengthWithLastStep(index);

        if (shiftActive.get()) {
            // NOTE: duplicate content when doubling the size of clip
            double curLen = cursorClip.getLoopLength().get();
            while (newLen % curLen == 0 && newLen > curLen) {
                curLen = curLen * 2;
                cursorClip.duplicateContent();
            }
        }

        adjustMode(newLen);
        cursorClip.getLoopLength().set(newLen);
    }

    private void adjustMode(final double clipLength) {
        final int notes = (int) (clipLength / 0.25);
        adjustMode(notes);
    }

    private void adjustMode(final int notes) {
        if (notes % 8 == 0) {
            cursorClip.launchMode().set("default");
        } else if (clipLaunchModeQuant.get()) {
            cursorClip.launchMode().set("synced");
        } else {
            cursorClip.launchMode().set("from_start");
        }
    }

    private void handleNoteStep(final NoteStep noteStep) {
        final int newStep = noteStep.x();

        assignments[newStep] = noteStep;
        // Wait for the new note itself: clearing the row first reports the step as empty, and
        // applying the values to that would use them up before the note exists.
        if (noteStep.state() == State.NoteOn) {
            final Consumer<NoteStep> change = expectedNoteChanges.remove(newStep);
            if (change != null) {
                change.accept(noteStep);
                if (expectedNoteChanges.isEmpty()) {
                    patternWritten(patternWriteId);
                }
            }
        }
    }

    /** @return the notes of the selected pad on the visible page, with all their values */
    List<StepData> snapshotPattern() {
        return getOnNotes().stream().map(StepData::of).collect(Collectors.toList());
    }

    /** Removes the notes of the selected pad from the clip on display. */
    void clearPattern() {
        expectedNoteChanges.clear();
        cursorClip.clearStepsAtY(0, 0);
    }

    /**
     * Writes the pattern for the selected pad into the clip on display.
     *
     * @param withValues false to write only position, velocity and length
     * @param whenDone runs once Bitwig has reported all the notes and they got their values, or after
     *                 a second at the latest
     */
    void writePattern(final List<StepData> pattern, final boolean withValues, final Runnable whenDone) {
        expectedNoteChanges.clear();
        final int id = ++patternWriteId;
        afterPatternWritten = whenDone;
        for (final StepData step : pattern) {
            if (withValues) {
                expectedNoteChanges.put(step.x(), step::applyTo);
            }
            cursorClip.setStep(step.x(), 0, step.midiVelocity(), step.duration());
        }
        if (expectedNoteChanges.isEmpty()) {
            patternWritten(id);
        } else {
            host.scheduleTask(() -> patternWritten(id), 1000);
        }
    }

    private void patternWritten(final int id) {
        if (id != patternWriteId || afterPatternWritten == null) {
            return;
        }
        final Runnable whenDone = afterPatternWritten;
        afterPatternWritten = null;
        // whatever did not arrive must not hit notes of the next clip on display
        expectedNoteChanges.clear();
        whenDone.run();
    }

    double getClipLength() {
        return cursorClip.getLoopLength().get();
    }

    void setClipLength(final double beats) {
        cursorClip.getLoopLength().set(beats);
    }

    private void applyValues(final NoteStep dest, final NoteStep src) {
        double chance = src.chance();
        if (chance == 0) {
            // TODO: this is an API bug. chance() returns 0
            chance = 1;
        }
        dest.setChance(chance);
        dest.setTimbre(src.timbre());
        dest.setPressure(src.pressure());
        dest.setRepeatCount(src.repeatCount());
        dest.setRepeatVelocityCurve(src.repeatVelocityCurve());
        dest.setPan(src.pan());
        dest.setRepeatVelocityEnd(src.repeatVelocityEnd());
        dest.setRecurrence(src.recurrenceLength(), src.recurrenceMask());
        dest.setOccurrence(src.occurrence());
    }

    private void handlePlayingStep(final int playingStep) {
        clipPlayingStep = playingStep;
        updatePlayingStep();
    }

    private void updatePlayingStep() {
        playingStep = clipPlayingStep == -1 ? -1 : clipPlayingStep - positionHandler.getStepOffset();
    }

    @Override
    protected void onActivate() {
        currentLayer = mainLayer;
        mainLayer.activate();
        encoderLayer.activate();
        padHandler.applyScale();
    }

    @Override
    protected void onDeactivate() {
        currentLayer.deactivate();
        encoderLayer.deactivate();
        padHandler.disableNotePlaying();
    }

    public void retrigger() {
        cursorClip.launch();
    }

    public void stop() {
        cursorClip.getTrack().stop();
    }

    public StepViewPosition getPositionHandler() {
        return positionHandler;
    }

    PinnableCursorClip getCursorClip() {
        return cursorClip;
    }

    boolean isShiftHeld() {
        return shiftActive.get();
    }

    /** Blink phase of whatever is marked as the source of a copy (step, pad or clip). */
    boolean isSourceBlinkDark() {
        return blinkState % 4 < 2;
    }

    boolean isCopyHeld() {
        return copyHeld.get();
    }

    boolean isDeleteHeld() {
        return deleteHeld.get();
    }

    boolean isSelectHeld() {
        return selectHeld.get();
    }

    public void abortRecurrenceEdit() {
        recurrenceEditor.abort();
    }

    public void exitRecurrenceEdit() {
        recurrenceEditor.exitRecurrenceEdit();
    }

    /** Shows the value on the second pad row, if it is one the bar knows (velocity, chance, repeats). */
    public void enterStepValueBar(final NoteStepAccess accessor) {
        if (StepValueBar.supports(accessor)) {
            // both use the second pad row
            recurrenceEditor.abort();
            stepValueBar.enter(accessor);
        }
    }

    /** The held step pressed last that has a note: the one the display and the second row show. */
    NoteStep getFocusNote() {
        final List<NoteStep> held = getHeldNotes();
        return held.isEmpty() ? null : held.get(held.size() - 1);
    }

    public void enterRecurrenceEdit() {
        stepValueBar.abort();
        recurrenceEditor.enterRecurrenceEdit();
    }

    public IntSetValue getHeldSteps() {
        return heldSteps;
    }

    public void clearExpectedNoteChanges() {
        expectedNoteChanges.clear();
    }

    public BooleanValueObject getLengthDisplay() {
        return lengthDisplay;
    }

    public void notifyMuteAction() {
        muteActionsTaken.set(true);
    }

    public void notifySoloAction() {
        soloActionsTaken.set(true);
    }

    public AccentHandler getAccentHandler() {
        return accentHandler;
    }

    public PadHandler getPadHandler() {
        return padHandler;
    }

    public void setActiveRemoteControlsPage(final CursorRemoteControlsPage remoteControlsPage) {
        this.activeRemoteControlsPage = remoteControlsPage;
    }

    public CursorRemoteControlsPage getActiveRemoteControlsPage() {
        return activeRemoteControlsPage;
    }

}
