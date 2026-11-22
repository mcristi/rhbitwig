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
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extensions.framework.values.Midi;

/**
 * Akai MIDI Mix as a mixer for 8 pads of the Drum Machine, living in the same extension as the Akai
 * Fire so that the Fire's display can show what the MIDI Mix is changing.
 * <ul>
 * <li>Faders: pad volume. Master fader: track volume.</li>
 * <li>Knob row 1: 2nd remote control of the pad's first device. Rows 2 and 3: two sends of the
 * pad, 1 and 2 or 3 and 4.</li>
 * <li>Mute buttons: switch the pad on that channel between sends 1-2 (LED off) and sends 3-4 (LED
 * on) for rows 2 and 3; each pad remembers its own. Solo: fill mode while held. Rec arm: remote controls page 2 of the track's last device. Bank left /
 * right: next / previous 8 pads.</li>
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
    private static final int NUM_SENDS = 4;
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
    // per pad (by key): false = knob rows 2 and 3 are sends 1 and 2, true = sends 3 and 4
    private final boolean[] secondSendPair = new boolean[TOTAL_PADS];
    private final boolean[] padExists = new boolean[TOTAL_PADS];
    private int scrollPosition = 0;
    private final CursorRemoteControlsPage[] chainRemoteControls = new CursorRemoteControlsPage[NUM_CHANNELS];
    private final AbsoluteHardwareKnob[][] hwKnobs = new AbsoluteHardwareKnob[NUM_KNOB_ROWS][NUM_CHANNELS];
    private final Send[][] chainSends = new Send[NUM_SENDS][NUM_CHANNELS];
    private final CursorRemoteControlsPage deviceButtonControls;
    private final boolean[] buttonState = new boolean[NUM_CHANNELS];

    // what the display is following: the parameter moved last and where it belongs
    private Parameter shownParameter;
    private String shownElement = "";
    private long shownSince = -1;
    private long lastRedraw = -1;
    private boolean redrawPending = false;
    // the values Bitwig reports for the knobs while starting up are not the user turning them
    private boolean started = false;
    private SettableBooleanValue fillMode;
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

        final PinnableCursorDevice lastDevice = cursorTrack.createCursorDevice("midi-mix-buttons", "Button Device",
                0, CursorDeviceFollowMode.LAST_DEVICE);
        lastDevice.name().markInterested();
        deviceButtonControls = lastDevice.createCursorRemoteControlsPage("Buttons", NUM_CHANNELS, "");
        deviceButtonControls.pageCount().addValueObserver(count -> {
            if (count > 1) {
                deviceButtonControls.selectedPageIndex().set(1);
            }
        });
        for (int i = 0; i < NUM_CHANNELS; i++) {
            watch(deviceButtonControls.getParameter(i));
            final int bi = i;
            deviceButtonControls.getParameter(i).value().addValueObserver(value -> {
                buttonState[bi] = value > 0.5;
                sendLed(NOTE_REC_ARM[bi], buttonState[bi] ? ON : OFF);
            });
        }

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
                updateSendBinding(i);
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
            watch(chainRemoteControls[i].getParameter(1));

            hwKnobs[0][i].setBinding(chainRemoteControls[i].getParameter(1));
            updateSendBinding(i);
        }
        // the Solo button's LED shows the fill mode, also when it is switched in Bitwig
        fillMode = host.createTransport().isFillModeActive();
        fillMode.addValueObserver(active -> sendLed(NOTE_SOLO, active ? ON : OFF));
    }

    private void onMidi(final int status, final int data1, final int data2) {
        final int type = status & 0xF0;

        if (type == Midi.CC) {
            handleCC(data1, data2);
        } else if (type == Midi.NOTE_ON && data2 > 0) {
            if (data1 == NOTE_SOLO) {
                setFill(true);
            } else {
                handleNoteOn(data1);
            }
        } else if ((type == Midi.NOTE_OFF || type == Midi.NOTE_ON) && data1 == NOTE_SOLO) {
            setFill(false);
        }
    }

    /** The Solo button: Bitwig's fill mode for as long as it is held (steps with a fill occurrence play). */
    private void setFill(final boolean active) {
        fillMode.set(active);
        if (active) {
            showMessage(cursorTrack.name().get(), "Fill", "On");
        } else {
            oled.clearScreenDelayed();
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
                toggleSendPair(i);
                return;
            }
        }

        for (int i = 0; i < NUM_CHANNELS; i++) {
            if (note == NOTE_REC_ARM[i]) {
                final double target = buttonState[i] ? 0.0 : 1.0;
                final Parameter parameter = deviceButtonControls.getParameter(i);
                parameter.set(target);
                follow(parameter, cursorTrack.name().get());
                return;
            }
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

    /** @return the parameter the knob is bound to, see updateSendBinding */
    private Parameter knobTarget(final int row, final int channel) {
        if (row == 0) {
            return chainRemoteControls[channel].getParameter(1);
        }
        // rows 2 and 3: first and second send of the channel's pair
        return chainSends[(isSecondSendPair(channel) ? 2 : 0) + row - 1][channel];
    }

    /** @return the key of the pad on the channel */
    private int padKey(final int channel) {
        return Math.min(TOTAL_PADS - 1, scrollPosition + channel);
    }

    private boolean isSecondSendPair(final int channel) {
        return secondSendPair[padKey(channel)];
    }

    /** Binds knob rows 2 and 3 of the channel to its current pair of sends and shows the pair on the LED. */
    private void updateSendBinding(final int channel) {
        hwKnobs[1][channel].setBinding(knobTarget(1, channel));
        hwKnobs[2][channel].setBinding(knobTarget(2, channel));
        final int led = isSecondSendPair(channel) ? ON : OFF;
        sendLed(NOTE_MUTE[channel], led);
        sendLed(NOTE_SOLO_MUTE[channel], led);
    }

    private void toggleSendPair(final int channel) {
        secondSendPair[padKey(channel)] = !isSecondSendPair(channel);
        updateSendBinding(channel);
        final String pair = isSecondSendPair(channel) ? "Sends 3-4" : "Sends 1-2";
        final String names = knobTarget(1, channel).name().get() + " / " + knobTarget(2, channel).name().get();
        showMessage(drumPadBank.getItemAt(channel).name().get(), pair, names);
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
