package com.akai.fire.sequence;

import com.akai.fire.ColorLookup;
import com.akai.fire.ColorUtils;
import com.akai.fire.display.ParameterDisplayBinding;
import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DrumPad;
import com.bitwig.extension.controller.api.Send;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.values.BooleanValueObject;
import com.bitwig.extensions.framework.values.SpecialDevices;

class PadContainer {

    private static final double SHIFT_INC = 0.001;
    private static final double REGULAR_INC = 0.025;
    // The key at which a sample loaded into Komplete Kontrol plays at its original pitch (C3)
    private static final int SAMPLE_ROOT_KEY = 60;
    // Note Transpose "Semis" goes from -48 to +48
    private static final int SEMIS_RANGE = 48;
    private static final String[] KEY_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    // how long the set-up of a new pad waits for Bitwig to create its devices (a plug-in can take a while)
    private static final long PENDING_TRANSPOSE_MS = 15000;
    // VST3 class id of Native Instruments Komplete Kontrol
    private static final String KOMPLETE_KONTROL_VST3_ID = "5653544E694B4B6B6F6D706C65746520";

    private final PadHandler padHandler;

    private RgbLigthState padColor;

    final DrumPad pad;
    final int index;

    private final BooleanValueObject playing;
    private boolean selected;
    private boolean exists;
    // transpose to set once the new Note Transpose of this pad has arrived
    private Integer pendingSemis;
    // the Note Transpose still has to be put in front of the pad's new first device
    private boolean pendingTransposeInsert;
    // name for the new pad, see setUpEmptyPad
    private String pendingName;
    // the window of the new pad's Komplete Kontrol still has to be closed
    private boolean pendingWindowClose;
    // the new pad's Komplete Kontrol still has to be selected, once it sits behind the Note Transpose
    private boolean pendingKontrolSelect;
    private long pendingSemisSince;

    private final ParameterDisplayBinding volumeBinding;
    private final ParameterDisplayBinding panBinding;
    private final ParameterDisplayBinding[] sendBindings = new ParameterDisplayBinding[8];

    private final DeviceBank deviceBank;
    private final CursorRemoteControlsPage remoteControls;
    private final ParameterDisplayBinding macro1Binding;
    private final ParameterDisplayBinding macro2Binding;
    private final ParameterDisplayBinding macro3Binding;
    private final ParameterDisplayBinding macro4Binding;
    private final ParameterDisplayBinding macro5Binding;
    private final ParameterDisplayBinding macro6Binding;
    private final ParameterDisplayBinding macro7Binding;
    private final ParameterDisplayBinding macro8Binding;

    public PadContainer(final PadHandler padHandler, final int index, final DrumPad pad,
                        final BooleanValueObject playing) {
        super();
        this.padHandler = padHandler;
        this.index = index;
        this.pad = pad;
        this.playing = playing;
        this.playing.markInterested();

        for (int i = 0; i < 8; i++) {
            final Send sendItem = pad.sendBank().getItemAt(i);
            sendBindings[i] = new ParameterDisplayBinding(i + 2, index, sendItem, padHandler.getDiplayTarget(), false);
        }

        pad.mute().markInterested();
        pad.solo().markInterested();
        pad.name().markInterested();
        pad.addIsSelectedInEditorObserver(selected -> handlePadSelection(index, selected));
        pad.exists().addValueObserver(exists -> this.exists = exists);

        padColor = RgbLigthState.OFF;
        pad.color().addValueObserver((r, g, b) -> {
            // NOTE: leave the hex here to get Bitwig colors
//            String hex = pad.color().get().toHex();
            padColor = ColorLookup.getColor(r, g, b);

            if (selected) {
                this.padHandler.currentPadColor = padColor;
            }
        });
        volumeBinding = new ParameterDisplayBinding(0, index, pad.volume(), padHandler.getDiplayTarget(), false);
        panBinding = new ParameterDisplayBinding(1, index, pad.pan(), padHandler.getDiplayTarget(), true);

        deviceBank = pad.createDeviceBank(2);
        deviceBank.getDevice(1).name().markInterested();

        remoteControls = deviceBank.getDevice(0).createCursorRemoteControlsPage(8);
        remoteControls.getName().markInterested();
        remoteControls.pageCount().markInterested();
        remoteControls.pageNames().markInterested();

        for (int i = 0; i < remoteControls.getParameterCount(); i++) {
            remoteControls.getParameter(i).value().markInterested();
            remoteControls.getParameter(i).name().markInterested();
        }
        remoteControls.getParameter(1).name().addValueObserver(name -> applyPendingTranspose());
        // the Komplete Kontrol is the first device until the Note Transpose is put in front of it
        for (int i = 0; i < 2; i++) {
            final Device device = deviceBank.getDevice(i);
            device.isWindowOpen().addValueObserver(open -> {
                if (open) {
                    closePendingWindow(device);
                }
            });
        }
        deviceBank.getDevice(1).exists().addValueObserver(secondDeviceExists -> selectPendingKontrol());
        deviceBank.getDevice(0).exists().addValueObserver(firstDeviceExists -> {
            if (firstDeviceExists) {
                insertPendingTranspose();
            }
        });

        macro1Binding = new ParameterDisplayBinding(4, index, remoteControls.getParameter(0), padHandler.getDiplayTarget(), false);
        macro2Binding = new ParameterDisplayBinding(5, index, remoteControls.getParameter(1), padHandler.getDiplayTarget(), false);
        macro3Binding = new ParameterDisplayBinding(6, index, remoteControls.getParameter(2), padHandler.getDiplayTarget(), false);
        macro4Binding = new ParameterDisplayBinding(7, index, remoteControls.getParameter(3), padHandler.getDiplayTarget(), false);
        macro5Binding = new ParameterDisplayBinding(8, index, remoteControls.getParameter(4), padHandler.getDiplayTarget(), false);
        macro6Binding = new ParameterDisplayBinding(9, index, remoteControls.getParameter(5), padHandler.getDiplayTarget(), false);
        macro7Binding = new ParameterDisplayBinding(10, index, remoteControls.getParameter(6), padHandler.getDiplayTarget(), false);
        macro8Binding = new ParameterDisplayBinding(11, index, remoteControls.getParameter(7), padHandler.getDiplayTarget(), false);
    }

    public void bindParameters(final Layer layer) {
        layer.addBinding(volumeBinding);
        layer.addBinding(panBinding);
        for (final ParameterDisplayBinding binding : sendBindings) {
            layer.addBinding(binding);
        }
    }

    public void bindMacros(final Layer layer) {
        layer.addBinding(macro1Binding);
        layer.addBinding(macro2Binding);
        layer.addBinding(macro3Binding);
        layer.addBinding(macro4Binding);
    }

    public void bindMacros2(final Layer layer) {
        layer.addBinding(macro5Binding);
        layer.addBinding(macro6Binding);
        layer.addBinding(macro7Binding);
        layer.addBinding(macro8Binding);
    }

    /** @return false for a pad of the Drum Machine that has nothing on it yet */
    public boolean exists() {
        return exists;
    }

    /**
     * Sets up an empty pad as a starting point for a synth or sample: a Komplete Kontrol with a Note
     * Transpose in front of it that moves the pad's key to C3, so that samples play at their original
     * pitch, and a random color.
     *
     * @param key the key the pad is played with
     */
    public void setUpEmptyPad(final int key) {
        pad.insertionPoint().insertVST3Device(KOMPLETE_KONTROL_VST3_ID);
        // The rest has to wait for Bitwig. Once the pad has its chain with the Komplete Kontrol it
        // gets its color and the Note Transpose (insertPendingTranspose), the plug-in window is
        // closed when it opens (closePendingWindow), and the Semis are set once the Note Transpose
        // exists (applyPendingTranspose).
        pendingTransposeInsert = true;
        pendingWindowClose = true;
        // otherwise every new pad would be called "Komplete Kontrol"
        pendingName = keyName(key);
        pendingSemis = originalPitchSemis(key);
        pendingSemisSince = System.currentTimeMillis();
    }

    /** @return the key as Bitwig shows it, e.g. 46 = A#1 (C3 = 60) */
    public static String keyName(final int key) {
        return KEY_NAMES[key % 12] + (key / 12 - 2);
    }

    /** @return the Note Transpose semitones that make the given key play a sample at its original pitch */
    public static int originalPitchSemis(final int key) {
        return Math.max(-SEMIS_RANGE, Math.min(SEMIS_RANGE, SAMPLE_ROOT_KEY - key));
    }

    /** @return true if the first device of the pad is a Note Transpose (its 2nd control is Semis) */
    private boolean startsWithNoteTranspose() {
        return getParam2Name().toLowerCase().contains("semi");
    }

    private boolean pendingSetUpExpired() {
        return System.currentTimeMillis() - pendingSemisSince > PENDING_TRANSPOSE_MS;
    }

    /** The pad's first device (the Komplete Kontrol) has arrived: put the Note Transpose before it. */
    private void insertPendingTranspose() {
        if (!pendingTransposeInsert) {
            return;
        }
        pendingTransposeInsert = false;
        if (!pendingSetUpExpired()) {
            pad.color().set(ColorUtils.getRandomColor());
            pad.name().set(pendingName);
            deviceBank.getDevice(0).beforeDeviceInsertionPoint()
                    .insertBitwigDevice(SpecialDevices.NOTE_TRANSPOSE.getUuid());
        }
    }

    /** Bitwig opens the window of a newly inserted plug-in; the new pad should stay in the background. */
    private void closePendingWindow(final Device device) {
        if (!pendingWindowClose) {
            return;
        }
        pendingWindowClose = false;
        if (!pendingSetUpExpired()) {
            device.isWindowOpen().set(false);
        }
    }

    private void applyPendingTranspose() {
        if (pendingSemis == null) {
            return;
        }
        if (pendingSetUpExpired()) {
            pendingSemis = null;
        } else if (startsWithNoteTranspose()) {
            setSemis(pendingSemis);
            pendingSemis = null;
            // the Note Transpose is in place, so the Komplete Kontrol is the second device now
            pendingKontrolSelect = true;
            selectPendingKontrol();
        }
    }

    /** Last step of the set-up: leave the Komplete Kontrol selected, ready to load a sound. */
    private void selectPendingKontrol() {
        if (!pendingKontrolSelect || !deviceBank.getDevice(1).exists().get()) {
            return;
        }
        pendingKontrolSelect = false;
        if (!pendingSetUpExpired()) {
            deviceBank.getDevice(1).selectInEditor();
        }
    }

    /** Sets the Semis of a Note Transpose at the start of the pad's chain. */
    public void setSemis(final int semis) {
        if (startsWithNoteTranspose()) {
            remoteControls.getParameter(1).value().setImmediately(0.5 + semis / (2.0 * SEMIS_RANGE));
        }
    }

    public RgbLigthState getPadColor() {
        return padColor;
    }

    public int getIndex() {
        return index;
    }

    private void handlePadSelection(final int index, final boolean selected) {
        this.selected = selected;
        if (this.selected) {
            padHandler.executePadSelection(this);
            padHandler.parent.setActiveRemoteControlsPage(remoteControls);
        }
    }

    public RgbLigthState mutingColors() {
        if (!exists) {
            return RgbLigthState.OFF;
        }
        if (pad.mute().get()) {
            return playing.returnTrueFalse(padColor.getDimmed(), padColor.getVeryDimmed());
        }

        return playing.returnTrueFalse(padColor.getBrightest(), padColor);
    }

    public RgbLigthState soloingColors() {
        if (!exists) {
            return RgbLigthState.OFF;
        }
        if (pad.solo().get()) {
            return playing.returnTrueFalse(padColor.getBrightest(), padColor);
        }

        return playing.returnTrueFalse(padColor.getDimmed(), padColor.getVeryDimmed());
    }

    public String getName() {
        return pad.name().get();
    }

    public String getParam1Name() {
        if (remoteControls.getParameterCount() >= 1) {
            return remoteControls.getParameter(0).name().get();
        }

        return "";
    }

    public String getParam2Name() {
        if (remoteControls.getParameterCount() >= 2) {
            return remoteControls.getParameter(1).name().get();
        }

        return "";
    }

    public String getParam5Name() {
        if (remoteControls.getParameterCount() >= 5) {
            return remoteControls.getParameter(4).name().get();
        }

        return "";
    }

    public String getParam8Name() {
        if (remoteControls.getParameterCount() >= 8) {
            return remoteControls.getParameter(7).name().get();
        }

        return "";
    }

    public RgbLigthState getColor() {
        // with Copy held the selected pad is what gets copied: blink it like a copied step
        if (selected && padHandler.parent.isCopyHeld() && padHandler.parent.isSourceBlinkDark()) {
            return RgbLigthState.GRAY_1;
        }
        if (selected) {
            return playing.returnTrueFalse(padColor.getBrightest(), padColor.getBrightend());
        }
        return playing.returnTrueFalse(padColor, padColor.getDimmed());
    }

    public void select() {
        pad.selectInEditor();
    }

    public void selectFistDevice() {
        final Device firstDevice = deviceBank.getDevice(0);
        if (firstDevice != null) {
            firstDevice.selectInEditor();
        }
    }

    public void selectRelevantDevice() {
        final Device secondDevice = deviceBank.getDevice(1);
        if (secondDevice.name().get().equals("Komplete Kontrol")) {
            secondDevice.selectInEditor();
        } else {
            this.selectFistDevice();
        }
    }

    public void modifyValue(final int typeIndex, final int inc, final boolean shiftHeld) {
        final double amount = inc * (shiftHeld ? SHIFT_INC : REGULAR_INC);
        switch (typeIndex) {
            case 0:
                volumeBinding.modify(amount);
                break;
            case 1:
                panBinding.modify(amount);
                break;
            case 2:
                sendBindings[0].modify(amount);
                break;
            case 3:
                sendBindings[1].modify(amount);
                break;
            case 4:
                macro1Binding.modify(amount);
                break;
            case 5:
                macro2Binding.modify(amount);
                break;
            case 6:
                macro3Binding.modify(amount);
                break;
            case 7:
                macro4Binding.modify(amount);
                break;
            case 8:
                macro5Binding.modify(amount);
                break;
            case 9:
                macro6Binding.modify(amount);
                break;
            case 10:
                macro7Binding.modify(amount);
                break;
            case 11:
                macro8Binding.modify(amount);
                break;
            default:
                break;
        }
    }

    public void resetMacroValue(final int macro) {
        switch (macro) {
            case 1:
                macro1Binding.reset();
                break;
            case 2:
                macro2Binding.reset();
                break;
            case 5:
                macro5Binding.reset();
                break;
            default:
                break;
        }
    }

    public void setMacro1Value(final double amount) {
        macro1Binding.modify(amount);
    }

    public double getMacro1Value() {
        return macro1Binding.getValue();
    }

    public void increaseMacro2Value(final int inc) {
        macro2Binding.increase(inc);
    }

    public double getMacro2Value() {
        return macro2Binding.getValue();
    }

    public void setMacro5Value(final double amount) {
        macro5Binding.modify(amount);
    }

    public double getMacro5Value() {
        return macro5Binding.getValue();
    }


    public void setMacro8Value(final double amount) {
        macro8Binding.modify(amount);
    }

    public double getMacro8Value() {
        return macro8Binding.getValue();
    }

    public void updateDisplay(final int typeIndex) {
        switch (typeIndex) {
            case 0:
                volumeBinding.update();
                break;
            case 1:
                panBinding.update();
                break;
            case 2:
                sendBindings[0].update();
                break;
            case 3:
                sendBindings[1].update();
                break;
            case 4:
                macro1Binding.update();
                break;
            case 5:
                macro2Binding.update();
                break;
            case 6:
                macro3Binding.update();
                break;
            case 7:
                macro4Binding.update();
                break;
            case 8:
                macro5Binding.update();
                break;
            case 9:
                macro6Binding.update();
                break;
            case 10:
                macro7Binding.update();
                break;
            case 11:
                macro8Binding.update();
                break;
            default:
                break;
        }
    }
}