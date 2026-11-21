package com.akai.fire.sequence;

import com.akai.fire.DrumSequencerExtension;
import com.akai.fire.ColorLookup;
import com.akai.fire.ColorUtils;
import com.akai.fire.control.RgbButton;
import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import com.bitwig.extension.controller.api.Project;
import com.bitwig.extensions.framework.Layer;

import java.util.List;

public class SeqClipHandler {

    private final DrumSequenceMode parent;
    private final PinnableCursorClip cursorClip;
    private int selectedSlotIndex = -1;
    private final ClipLauncherSlotBank slotBank;
    private final RgbLigthState[] slotColors = new RgbLigthState[16];
    private final CursorTrack cursorTrack;
    private int blinkState = 0;

    // how long an action waits for Bitwig to create the scenes of its slot
    private static final long PENDING_SLOT_MS = 5000;
    private final Project project;
    private final ControllerHost host;
    // what to do with a slot once the scene it needed exists, see createScenesUpTo
    private Runnable pendingSlotAction;
    private int pendingSlotIndex = -1;
    private long pendingSlotSince;

    // Copy of one pad's pattern into another clip (Shift + Copy + clip), see copyPatternTo.
    // Runs once the target clip is reported as selected.
    private Runnable pendingPatternCopy;
    private int patternCopyTarget = -1;
    private int patternCopyId = 0;

    public SeqClipHandler(final DrumSequencerExtension driver, final DrumSequenceMode parent, final Layer clipLayer) {
        this.parent = parent;
        this.cursorClip = parent.getCursorClip();
        cursorTrack = driver.getViewControl().getCursorTrack();
        slotBank = cursorTrack.clipLauncherSlotBank();
        cursorTrack.exists().markInterested();
        host = driver.host;
        project = host.getProject();
        initClipControlButtons(clipLayer, driver);
    }

    private void initClipControlButtons(final Layer clipLayer, final DrumSequencerExtension driver) {
        final RgbButton[] rgbButtons = driver.getRgbButtons();
        for (int i = 0; i < 16; i++) {
            final RgbButton button = rgbButtons[i + 16];
            final int index = i;

            final ClipLauncherSlot cs = slotBank.getItemAt(index);

            cs.color().addValueObserver((r, g, b) -> {
                slotColors[index] = ColorLookup.getColor(r, g, b);
            });
            cs.isSelected().addValueObserver(selected -> {
                if (selected) {
                    selectedSlotIndex = index;
                    if (index == patternCopyTarget && pendingPatternCopy != null) {
                        final Runnable copy = pendingPatternCopy;
                        pendingPatternCopy = null;
                        copy.run();
                    }
                }
            });
            slotColors[index] = ColorLookup.getColor(cs.color().get());
            cs.exists().addValueObserver(exists -> {
                if (exists) {
                    runPendingSlotAction(index);
                }
            });
            cs.hasContent().markInterested();
            cs.isPlaybackQueued().markInterested();
            cs.isPlaying().markInterested();
            cs.isRecording().markInterested();
            cs.isRecordingQueued().markInterested();
            cs.isSelected().markInterested();
            cs.isStopQueued().markInterested();

            button.bindPressed(clipLayer, p -> handleClip(index, cs, p), () -> getClipSate(index, cs));
        }
    }

    private RgbLigthState getClipSate(final int index, final ClipLauncherSlot slot) {
        if (slot.hasContent().get()) {
            if (slotColors[index] == null) {
                return RgbLigthState.OFF;
            }
            final RgbLigthState color = slotColors[index];
            // with Copy held the selected clip is what gets copied: blink it like a copied step
            if (parent.isCopyHeld() && index == selectedSlotIndex && parent.isSourceBlinkDark()) {
                return RgbLigthState.GRAY_1;
            }
            if (slot.isSelected().get()) {

                if (slot.isPlaying().get()) {
                    return blinkSlow(color.getBrightest(), color);
                }
                if (slot.isPlaybackQueued().get()) {
                    return blinkFast(color.getBrightest(), color.getDimmed());
                }
                return color.getBrightend();
            } else {
                if (slot.isPlaying().get()) {
                    return blinkSlow(color, color.getDimmed());
                }
                if (slot.isPlaybackQueued().get()) {
                    return blinkFast(color, color.getDimmed());
                }
                return color.getDimmed();
            }
        }
        return RgbLigthState.OFF;
    }

    private RgbLigthState blinkSlow(final RgbLigthState on, final RgbLigthState off) {
        if (blinkState % 8 < 4) {
            return on;
        }
        return off;
    }

    private RgbLigthState blinkFast(final RgbLigthState on, final RgbLigthState off) {
        if (blinkState % 2 == 0) {
            return on;
        }
        return off;
    }

    private void handleClip(final int index, final ClipLauncherSlot slot, final boolean pressed) {
        if (!pressed) {
            return;
        }
        if (parent.isCopyHeld() && parent.isShiftHeld()) {
            // only the selected pad's pattern, not the whole clip
            if (slot.exists().get()) {
                copyPatternTo(index, slot);
            } else {
                createScenesUpTo(index, () -> copyPatternTo(index, slot));
            }
            return;
        }
        final boolean hasContent = slot.hasContent().get();
        if (hasContent) {
            if (parent.isDeleteHeld()) {
                // NOTE: cursorClip.clearSteps removes the automations as well, so we avoid using it
                slot.deleteObject();
//                if (parent.isShiftHeld()) { // SHIFT + DELETE => remove clip
//                    slot.deleteObject();
//                } else { // DELETE => clear all steps
//                    final int previous = selectedSlotIndex;
//                    slot.select();
//                    cursorClip.clearSteps();
//                    if (previous != -1) {
//                        slotBank.getItemAt(previous).select();
//                    }
//                }
            } else if (parent.isCopyHeld()) { // copies note
                if (selectedSlotIndex != -1 && selectedSlotIndex != index) {
                    slot.replaceInsertionPoint().copySlotsOrScenes(slotBank.getItemAt(selectedSlotIndex));
                }
            } else if (parent.isSelectHeld()) {
                slot.select();
            } else if (parent.isShiftHeld()) {
                slot.color().set(ColorUtils.getColor(slot));
            } else {
                slot.select();
                slot.launch();
            }
        } else if (!slot.exists().get()) {
            // The project has fewer scenes than this slot needs. Add them for the two deliberate
            // actions (copy a clip here, new clip here) and act once Bitwig reports the slot.
            if (parent.isCopyHeld()) {
                final int source = selectedSlotIndex;
                if (source != -1 && source != index) {
                    createScenesUpTo(index,
                            () -> slot.replaceInsertionPoint().copySlotsOrScenes(slotBank.getItemAt(source)));
                }
            } else if (!parent.isSelectHeld() && !parent.isShiftHeld() && !parent.isDeleteHeld()) {
                createScenesUpTo(index, () -> createClip(index, slot));
            }
        } else {
            if (parent.isCopyHeld()) {
                if (selectedSlotIndex != -1 && selectedSlotIndex != index) {
                    slot.replaceInsertionPoint().copySlotsOrScenes(slotBank.getItemAt(selectedSlotIndex));
                }
            } else {
                createClip(index, slot);
            }
        }
    }

    /**
     * Copies the pattern of the selected pad from the selected clip into the clip of the given slot,
     * onto the same pad, replacing what that pad had there. An empty slot gets a clip of the same
     * length first. Notes can only be written into the clip on display, so this goes there, writes,
     * and comes back to the source clip.
     */
    private void copyPatternTo(final int index, final ClipLauncherSlot slot) {
        final int source = selectedSlotIndex;
        if (patternCopyTarget != -1 || source == -1 || source == index) {
            return;
        }
        final List<StepData> pattern = parent.snapshotPattern();
        if (pattern.isEmpty()) {
            // an empty pattern would only wipe the pad in the target clip
            parent.getOled().paramInfo("Copy pattern", "No notes", parent.getPadInfo());
            parent.getOled().clearScreenDelayed();
            return;
        }
        final boolean newClip = !slot.hasContent().get();
        final double length = parent.getClipLength();
        if (newClip) {
            slot.createEmptyClip(Math.max(1, (int) Math.ceil(length)));
        }
        final int id = ++patternCopyId;
        patternCopyTarget = index;
        pendingPatternCopy = () -> {
            if (newClip) {
                parent.setClipLength(length);
            }
            parent.clearPattern();
            // give Bitwig's reports about the target clip's old notes time to pass before the new
            // notes are expected
            host.scheduleTask(() -> {
                if (id == patternCopyId && patternCopyTarget == index) {
                    parent.writePattern(pattern, true, () -> finishPatternCopy(id, source));
                }
            }, 100);
        };
        parent.getOled().paramInfo("Copy pattern", "to clip " + (index + 1), parent.getPadInfo());
        parent.getOled().clearScreenDelayed();
        slot.select();
        // never stay stuck if Bitwig does not report the selection
        host.scheduleTask(() -> {
            if (id == patternCopyId && pendingPatternCopy != null) {
                pendingPatternCopy = null;
                patternCopyTarget = -1;
            }
        }, 2000);
    }

    private void finishPatternCopy(final int id, final int source) {
        if (id != patternCopyId) {
            return;
        }
        patternCopyTarget = -1;
        slotBank.getItemAt(source).select();
    }

    /** Adds scenes at the end of the project until the slot exists, then runs the action on it. */
    private void createScenesUpTo(final int index, final Runnable action) {
        if (!cursorTrack.exists().get()) {
            return;
        }
        int lastExisting = -1;
        for (int i = index - 1; i >= 0 && lastExisting == -1; i--) {
            if (slotBank.getItemAt(i).exists().get()) {
                lastExisting = i;
            }
        }
        pendingSlotAction = action;
        pendingSlotIndex = index;
        pendingSlotSince = System.currentTimeMillis();
        for (int i = lastExisting; i < index; i++) {
            project.createScene();
        }
    }

    private void runPendingSlotAction(final int index) {
        if (pendingSlotAction == null || index != pendingSlotIndex) {
            return;
        }
        final Runnable action = pendingSlotAction;
        pendingSlotAction = null;
        pendingSlotIndex = -1;
        if (System.currentTimeMillis() - pendingSlotSince <= PENDING_SLOT_MS) {
            action.run();
        }
    }

    /** New clip in an empty slot: a copy of the clip before it (keeps automation), else an empty one. */
    private void createClip(final int index, final ClipLauncherSlot slot) {
        int prevClipIndex = index - 1;
        int nonEmptyClipIndex = -1;

        while (nonEmptyClipIndex == -1 && prevClipIndex > -1) {
            final ClipLauncherSlot scene = slotBank.getItemAt(prevClipIndex);
            if (scene.hasContent().get()) {
                nonEmptyClipIndex = prevClipIndex;
            }
            prevClipIndex--;
        }

        if (nonEmptyClipIndex == -1) {
            slot.createEmptyClip(4);
        } else {
            slot.replaceInsertionPoint().copySlotsOrScenes(slotBank.getItemAt(nonEmptyClipIndex));
            slot.select();
        }
    }

    public void notifyBlink(final int blinkState) {
        this.blinkState = blinkState;
    }

}
