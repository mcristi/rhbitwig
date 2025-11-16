package com.akai.fire.sequence;

import com.akai.fire.ColorLookup;
import com.akai.fire.display.ParameterDisplayBinding;
import com.akai.fire.lights.RgbLigthState;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DrumPad;
import com.bitwig.extension.controller.api.Send;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.values.BooleanValueObject;
import com.bitwig.extensions.framework.values.DawColor;

class PadContainer {

    private static final double SHIFT_INC = 0.001;
    private static final double REGULAR_INC = 0.025;

    private static final RgbLigthState TR_RED = new RgbLigthState(70, 0, 0, true);
    private static final RgbLigthState TR_ORANGE = new RgbLigthState(90, 15, 0, true);
    private static final RgbLigthState TR_YELLOW = new RgbLigthState(110, 55, 0, true);
    private static final RgbLigthState TR_WHITE = new RgbLigthState(80, 80, 80, true);

    private static final RgbLigthState[] fixedPadColorTable = {TR_RED, TR_RED, TR_RED, TR_RED, //
            TR_ORANGE, TR_ORANGE, TR_ORANGE, TR_ORANGE, TR_YELLOW, TR_YELLOW, TR_YELLOW, TR_YELLOW, //
            TR_WHITE, TR_WHITE, TR_WHITE, TR_WHITE};

    private final PadHandler padHandler;

    private RgbLigthState padColor;
    private RgbLigthState bitwigPadColor = RgbLigthState.OFF;

    private final RgbLigthState muteColor = ColorLookup.getColor(DawColor.LIGHT_BROWN);
    private final RgbLigthState soloColor = ColorLookup.getColor(DawColor.BLUISH_GREEN);

    final DrumPad pad;
    final int index;

    private final BooleanValueObject playing;
    private boolean selected;
    private boolean exists;

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
        //padColor = fixedPadColorTable[index];
        padColor = RgbLigthState.OFF;
        pad.color().addValueObserver((r, g, b) -> {
            padColor = ColorLookup.getColor(r, g, b);
            bitwigPadColor = ColorLookup.getColor(r, g, b);
            // padColor = fixedPadColorTable[index];
            if (selected) {
                this.padHandler.currentPadColor = bitwigPadColor;
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

    public RgbLigthState getPadColor() {
        return padColor;
    }

    public RgbLigthState getBitwigPadColor() {
        return bitwigPadColor;
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
//            return playing.returnTrueFalse(muteColor.getDimmed(), muteColor.getVeryDimmed());
            return playing.returnTrueFalse(padColor.getDimmed(), padColor.getVeryDimmed());
        }
//        return playing.returnTrueFalse(muteColor.getBrightest(), muteColor);
        return playing.returnTrueFalse(padColor.getBrightest(), padColor);
    }

    public RgbLigthState soloingColors() {
        if (!exists) {
            return RgbLigthState.OFF;
        }
        if (pad.solo().get()) {
//            return playing.returnTrueFalse(soloColor.getBrightest(), soloColor);
            return playing.returnTrueFalse(padColor.getBrightest(), padColor);
        }
//        return playing.returnTrueFalse(soloColor.getDimmed(), soloColor.getVeryDimmed());
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

    public String getParam8Name() {
        if (remoteControls.getParameterCount() >= 8) {
            return remoteControls.getParameter(7).name().get();
        }

        return "";
    }

    public RgbLigthState getColor() {
//        if (!exists) {
//            return RgbLigthState.OFF;
//        }
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
            case 8:
                macro5Binding.update();
            case 9:
                macro6Binding.update();
            case 10:
                macro7Binding.update();
            case 11:
                macro8Binding.update();
                break;
            default:
                break;
        }
    }
}