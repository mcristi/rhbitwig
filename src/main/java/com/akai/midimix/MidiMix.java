package com.akai.midimix;

import com.akai.fire.display.OledDisplay;
import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DrumPad;
import com.bitwig.extension.controller.api.DrumPadBank;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.Send;
import com.bitwig.extensions.framework.values.Midi;

/**
 * Akai MIDI Mix as a mixer for 8 pads of the Drum Machine, living in the same extension as the Akai
 * Fire so that the Fire's display can show what the MIDI Mix is changing.
 * <ul>
 * <li>Faders: pad volume. Master fader: track volume.</li>
 * <li>The three knobs of a channel, top to bottom, in one of three layouts: hybrid = 2nd remote
 * control of the pad's first device, send 1, send 2; parameters = 1st, 2nd, 3rd remote control;
 * sends = send 1, 2, 3.</li>
 * <li>Mute button: parameters layout for the pad on that channel (LED on), again: back to hybrid.
 * Rec arm button: the same for the sends layout. Each pad remembers its layout. Solo: all pads back
 * to hybrid. Bank left / right: next / previous 8 pads.</li>
 * </ul>
 * The 8 pads always are the left or the right half of the 16 pads on the Fire: the bank buttons go
 * from half to half and take the Fire's page along, and the Fire's page buttons take the MIDI Mix
 * along. Each change flashes the 8 pads on the Fire.
 */
public class MidiMix {

    /** The 16 drum pads on the Fire, which the 8 channels of the MIDI Mix are tied to. */
    public interface FirePads {
        /** @return the key of the first of the 16 pads */
        int position();

        /**
         * Moves the Fire one page of 16 pads.
         *
         * @param dir +1 for up, -1 for down
         * @return false if there is no page in that direction
         */
        boolean scrollPage(int dir);

        /** Flashes the pads of the given keys, to show what the MIDI Mix covers now. */
        void flash(int firstKey, int count);
    }

    private static final int NUM_CHANNELS = 8;

    // Default Akai MIDI Mix CC assignments
    // Faders (CC per column)
    private static final int[] CC_FADERS = {19, 23, 27, 31, 49, 53, 57, 61};
    private static final int CC_MASTER_FADER = 62;

    // Knobs: 3 rows per column
    private static final int[][] CC_KNOBS = {
            {16, 20, 24, 28, 46, 50, 54, 58},
            {17, 21, 25, 29, 47, 51, 55, 59},
            {18, 22, 26, 30, 48, 52, 56, 60},
    };
    private static final int NUM_KNOB_ROWS = CC_KNOBS.length;

    // Buttons send/receive Note On/Off: note numbers double as LED addresses
    private static final int[] NOTE_MUTE = {1, 4, 7, 10, 13, 16, 19, 22};
    private static final int[] NOTE_SOLO_MUTE = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int[] NOTE_REC_ARM = {3, 6, 9, 12, 15, 18, 21, 24};
    private static final int NOTE_BANK_LEFT = 25;
    private static final int NOTE_BANK_RIGHT = 26;
    private static final int NOTE_SOLO = 27;
    private static final int ON = 127;
    private static final int OFF = 0;

    private static final int TOTAL_PADS = 128;
    private static final double VOLUME_0DB = 0.7937005259840997;
    private static final int NUM_SENDS = 3;
    // the remote control of a pad's first device on the top knob in the hybrid layout (0 = the first)
    private static final int HYBRID_CONTROL = 1;

    /** What the three knobs of a channel are on. */
    private enum KnobLayout {
        HYBRID("Hybrid"), PARAMETERS("Parameters"), SENDS("Sends");

        private final String title;

        KnobLayout(final String title) {
            this.title = title;
        }
    }

    private static final String[] KEY_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    // The display follows a control for this long after its last movement, and is redrawn at most
    // this often: a moving fader sends far more values than the display can take.
    private static final long SHOWN_MS = 1500;
    private static final long REDRAW_MS = 60;

    private final ControllerHost host;
    private final MidiOut midiOut;
    private final OledDisplay oled;
    private final CursorTrack cursorTrack;
    private final DrumPadBank drumPadBank;
    private boolean hasDrumPads = false;
    private final boolean[] padExists = new boolean[TOTAL_PADS];
    // per pad (by key)
    private final KnobLayout[] knobLayout = new KnobLayout[TOTAL_PADS];
    private int scrollPosition = 0;
    private final CursorRemoteControlsPage[] chainRemoteControls = new CursorRemoteControlsPage[NUM_CHANNELS];
    private final AbsoluteHardwareKnob[][] hwKnobs = new AbsoluteHardwareKnob[NUM_KNOB_ROWS][NUM_CHANNELS];
    private final Send[][] chainSends = new Send[NUM_SENDS][NUM_CHANNELS];

    // what the display is following: the parameter moved last and where it belongs
    private Parameter shownParameter;
    private String shownElement = "";
    private long shownSince = -1;
    private long lastRedraw = -1;
    private boolean redrawPending = false;
    // the values Bitwig reports for the knobs while starting up are not the user turning them
    private boolean started = false;
    private FirePads fire;
    // which 8 of the Fire's 16 pads: 0 = the first (left) half, 1 = the second
    private int half = 0;
    // a bank button was pressed: show the new range on the display when the position arrives
    private long bankPressedAt = -1;

    /**
     * @param cursorTrack the track the Fire works on, so that both controllers follow (and pin) the
     *                    same track
     */
    public MidiMix(final ControllerHost host, final HardwareSurface surface, final MidiIn midiIn,
                   final MidiOut midiOut, final CursorTrack cursorTrack, final OledDisplay oled) {
        this.host = host;
        this.midiOut = midiOut;
        this.oled = oled;
        this.cursorTrack = cursorTrack;

        java.util.Arrays.fill(knobLayout, KnobLayout.HYBRID);
        midiIn.setMidiCallback(this::onMidi);
        host.scheduleTask(() -> started = true, 1000);

        for (int row = 0; row < NUM_KNOB_ROWS; row++) {
            for (int col = 0; col < NUM_CHANNELS; col++) {
                final AbsoluteHardwareKnob knob = surface.createAbsoluteHardwareKnob("MIDIMIX_KNOB_" + row + "_" + col);
                knob.setAdjustValueMatcher(midiIn.createAbsoluteCCValueMatcher(0, CC_KNOBS[row][col]));
                // The knobs change what they control (the pad when the bank moves, the pair of
                // sends), so they keep Bitwig's takeover: a knob only takes hold once it has
                // reached the parameter's value, instead of making it jump.
                hwKnobs[row][col] = knob;
                // Bitwig keeps the messages of a bound knob to itself, they never reach onMidi:
                // the knob's own value is what tells that it was turned
                final int knobRow = row;
                final int channel = col;
                knob.value().addValueObserver(value -> {
                    if (started) {
                        followPad(channel, knobTarget(knobRow, channel));
                    }
                });
            }
        }

        cursorTrack.name().markInterested();
        watch(cursorTrack.volume());
        final PinnableCursorDevice cursorDevice = cursorTrack.createCursorDevice("midi-mix-drum", "Drum Machine",
                NUM_SENDS, CursorDeviceFollowMode.FIRST_INSTRUMENT);
        drumPadBank = cursorDevice.createDrumPadBank(NUM_CHANNELS);

        cursorDevice.hasDrumPads().addValueObserver(hasPads -> hasDrumPads = hasPads);

        final DrumPadBank monitorBank = cursorDevice.createDrumPadBank(TOTAL_PADS);
        monitorBank.setIndication(false);
        for (int i = 0; i < TOTAL_PADS; i++) {
            final int pi = i;
            monitorBank.getItemAt(i).exists().addValueObserver(exists -> {
                padExists[pi] = exists;
                updateBankLeds();
            });
        }

        drumPadBank.scrollPosition().addValueObserver(pos -> {
            scrollPosition = pos;
            updateBankLeds();
            // other pads: each has its own pair of sends
            for (int i = 0; i < NUM_CHANNELS; i++) {
                updateBindings(i);
            }
            if (started && fire != null) {
                fire.flash(pos, NUM_CHANNELS);
            }
            if (System.currentTimeMillis() - bankPressedAt < SHOWN_MS) {
                bankPressedAt = -1;
                showBank();
            }
        });

        for (int i = 0; i < NUM_CHANNELS; i++) {
            final DrumPad pad = drumPadBank.getItemAt(i);
            watch(pad.volume());
            pad.exists().markInterested();
            pad.name().markInterested();

            for (int s = 0; s < NUM_SENDS; s++) {
                chainSends[s][i] = pad.sendBank().getItemAt(s);
                watch(chainSends[s][i]);
            }

            final DeviceBank padDeviceBank = pad.createDeviceBank(1);
            final Device firstDevice = padDeviceBank.getDevice(0);
            chainRemoteControls[i] = firstDevice.createCursorRemoteControlsPage("Knobs", NUM_KNOB_ROWS, "");
            chainRemoteControls[i].selectedPageIndex().set(0);
            for (int row = 0; row < NUM_KNOB_ROWS; row++) {
                watch(chainRemoteControls[i].getParameter(row));
            }

            updateBindings(i);
        }
        sendLed(NOTE_SOLO, OFF);
    }

    private void onMidi(final int status, final int data1, final int data2) {
        final int type = status & 0xF0;

        if (type == Midi.CC) {
            handleCC(data1, data2);
        } else if (type == Midi.NOTE_ON && data2 > 0) {
            handleNoteOn(data1);
        }
    }

    private void handleCC(final int cc, final int value) {
        if (cc == CC_MASTER_FADER) {
            cursorTrack.volume().set(value / 127.0 * VOLUME_0DB);
            follow(cursorTrack.volume(), cursorTrack.name().get());
            return;
        }

        for (int i = 0; i < NUM_CHANNELS; i++) {
            if (cc == CC_FADERS[i]) {
                if (hasDrumPads) {
                    final DrumPad pad = drumPadBank.getItemAt(i);
                    pad.volume().set(value / 127.0 * VOLUME_0DB);
                    followPad(i, pad.volume());
                }
                return;
            }
        }

    }

    private void handleNoteOn(final int note) {
        for (int i = 0; i < NUM_CHANNELS; i++) {
            if (note == NOTE_MUTE[i] || note == NOTE_SOLO_MUTE[i]) {
                toggleLayout(i, KnobLayout.PARAMETERS);
                return;
            }
        }

        for (int i = 0; i < NUM_CHANNELS; i++) {
            if (note == NOTE_REC_ARM[i]) {
                toggleLayout(i, KnobLayout.SENDS);
                return;
            }
        }

        if (note == NOTE_SOLO) {
            resetLayouts();
            return;
        }

        if (note == NOTE_BANK_LEFT) {
            scrollBank(1);
            return;
        }
        if (note == NOTE_BANK_RIGHT) {
            scrollBank(-1);
        }
    }

    /** @return the key of the first of the 8 drum pads the channels are on */
    public int getPosition() {
        return scrollPosition;
    }

    /** Ties the 8 channels to the Fire's 16 pads, see the class description. */
    public void linkTo(final FirePads firePads) {
        fire = firePads;
    }

    /** The Fire shows another page: stay on the same half of it. */
    public void firePageChanged(final int firePosition) {
        drumPadBank.scrollPosition().set(Math.min(TOTAL_PADS - NUM_CHANNELS, firePosition + half * NUM_CHANNELS));
    }

    /** Next / previous 8 pads: the other half of the Fire's page, or the adjoining half of its next page. */
    private void scrollBank(final int dir) {
        bankPressedAt = System.currentTimeMillis();
        if (fire == null) {
            if (dir > 0) {
                drumPadBank.scrollPageForwards();
            } else {
                drumPadBank.scrollPageBackwards();
            }
        } else if (dir > 0 && half == 0 || dir < 0 && half == 1) {
            half = 1 - half;
            firePageChanged(fire.position());
        } else if (fire.scrollPage(dir)) {
            // firePageChanged follows when Bitwig reports the Fire's new page
            half = 1 - half;
        } else {
            // no further page: show where we are
            bankPressedAt = -1;
            showBank();
        }
    }

    /** @return the parameter the knob is bound to, see updateBindings */
    private Parameter knobTarget(final int row, final int channel) {
        switch (knobLayout[padKey(channel)]) {
        case PARAMETERS:
            return chainRemoteControls[channel].getParameter(row);
        case SENDS:
            return chainSends[row][channel];
        default:
            return row == 0 ? chainRemoteControls[channel].getParameter(HYBRID_CONTROL) : chainSends[row - 1][channel];
        }
    }

    /** @return the key of the pad on the channel */
    private int padKey(final int channel) {
        return Math.min(TOTAL_PADS - 1, scrollPosition + channel);
    }

    /**
     * Binds the three knobs of the channel according to the layout of the pad on it, and shows the
     * layout on the buttons: mute LED = parameters, rec arm LED = sends, none = hybrid.
     */
    private void updateBindings(final int channel) {
        for (int row = 0; row < NUM_KNOB_ROWS; row++) {
            hwKnobs[row][channel].setBinding(knobTarget(row, channel));
        }
        final KnobLayout layout = knobLayout[padKey(channel)];
        final int parametersLed = layout == KnobLayout.PARAMETERS ? ON : OFF;
        sendLed(NOTE_MUTE[channel], parametersLed);
        sendLed(NOTE_SOLO_MUTE[channel], parametersLed);
        sendLed(NOTE_REC_ARM[channel], layout == KnobLayout.SENDS ? ON : OFF);
    }

    /** The pad on the channel goes to the layout, or back to hybrid if it already is on it. */
    private void toggleLayout(final int channel, final KnobLayout layout) {
        final int key = padKey(channel);
        knobLayout[key] = knobLayout[key] == layout ? KnobLayout.HYBRID : layout;
        updateBindings(channel);
        // the three knobs top to bottom, as far as they fit on the display
        final StringBuilder names = new StringBuilder();
        for (int row = 0; row < NUM_KNOB_ROWS; row++) {
            names.append(row == 0 ? "" : "/").append(knobTarget(row, channel).name().get());
        }
        showMessage(drumPadBank.getItemAt(channel).name().get(), knobLayout[key].title, names.toString());
    }

    /** Solo: every pad back to the hybrid layout. */
    private void resetLayouts() {
        java.util.Arrays.fill(knobLayout, KnobLayout.HYBRID);
        for (int i = 0; i < NUM_CHANNELS; i++) {
            updateBindings(i);
        }
        showMessage(cursorTrack.name().get(), "MIDI Mix", "All hybrid");
    }

    // ---- display on the Fire

    /** Prepares a parameter for being shown: its values have to be known and changes redraw it. */
    private void watch(final Parameter parameter) {
        parameter.name().markInterested();
        parameter.value().markInterested();
        parameter.displayedValue().addValueObserver(value -> {
            if (parameter == shownParameter && System.currentTimeMillis() - shownSince < SHOWN_MS) {
                requestRedraw();
            }
        });
    }

    private void followPad(final int channel, final Parameter parameter) {
        final DrumPad pad = drumPadBank.getItemAt(channel);
        if (hasDrumPads && pad.exists().get()) {
            follow(parameter, pad.name().get());
        }
    }

    /** A control was moved: show its parameter now and keep the display on it while it changes. */
    private void follow(final Parameter parameter, final String element) {
        shownParameter = parameter;
        shownElement = element;
        shownSince = System.currentTimeMillis();
        requestRedraw();
    }

    private void requestRedraw() {
        final long now = System.currentTimeMillis();
        if (now - lastRedraw >= REDRAW_MS) {
            redraw();
        } else if (!redrawPending) {
            redrawPending = true;
            host.scheduleTask(() -> {
                redrawPending = false;
                redraw();
            }, REDRAW_MS);
        }
    }

    private void redraw() {
        if (shownParameter == null) {
            return;
        }
        lastRedraw = System.currentTimeMillis();
        oled.parameterInfo(shownElement, shownParameter.name().get(), shownParameter.value().get(),
                shownParameter.displayedValue().get(), false);
        oled.clearScreenDelayed();
    }

    private void showMessage(final String element, final String title, final String text) {
        shownParameter = null;
        oled.paramInfo(title, text, element);
        oled.clearScreenDelayed();
    }

    /** Shows which keys the 8 channels cover. */
    private void showBank() {
        int used = 0;
        for (int i = scrollPosition; i < scrollPosition + NUM_CHANNELS && i < TOTAL_PADS; i++) {
            if (padExists[i]) {
                used++;
            }
        }
        final String range = keyName(scrollPosition) + " - "
                + keyName(Math.min(TOTAL_PADS - 1, scrollPosition + NUM_CHANNELS - 1));
        showMessage(used + " of " + NUM_CHANNELS + " pads used", "MIDI Mix", range);
    }

    /** @return the key as Bitwig shows it, e.g. 46 = A#1 (C3 = 60) */
    private static String keyName(final int key) {
        return KEY_NAMES[key % 12] + (key / 12 - 2);
    }

    // ---- LEDs

    private boolean pageHasContent(final int startIndex) {
        if (startIndex < 0 || startIndex >= TOTAL_PADS) {
            return false;
        }
        for (int i = startIndex; i < startIndex + NUM_CHANNELS && i < TOTAL_PADS; i++) {
            if (padExists[i]) {
                return true;
            }
        }
        return false;
    }

    private void updateBankLeds() {
        sendLed(NOTE_BANK_LEFT, pageHasContent(scrollPosition + NUM_CHANNELS) ? ON : OFF);
        sendLed(NOTE_BANK_RIGHT, pageHasContent(scrollPosition - NUM_CHANNELS) ? ON : OFF);
    }

    private void sendLed(final int note, final int value) {
        midiOut.sendMidi(Midi.NOTE_ON, note, value);
    }

    public void exit() {
        for (int i = 0; i < NUM_CHANNELS; i++) {
            sendLed(NOTE_MUTE[i], OFF);
            sendLed(NOTE_SOLO_MUTE[i], OFF);
            sendLed(NOTE_REC_ARM[i], OFF);
        }
        sendLed(NOTE_BANK_LEFT, OFF);
        sendLed(NOTE_BANK_RIGHT, OFF);
        sendLed(NOTE_SOLO, OFF);
    }
}
