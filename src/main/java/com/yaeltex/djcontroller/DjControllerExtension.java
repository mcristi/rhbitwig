package com.yaeltex.djcontroller;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.AbsoluteHardwareControl;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.DrumPad;
import com.bitwig.extension.controller.api.DrumPadBank;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.Send;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.di.Context;
import com.bitwig.extensions.framework.values.BooleanValueObject;
import com.yaeltex.common.YaeltexButtonLedState;
import com.yaeltex.common.YaeltexMidiProcessor;
import com.yaeltex.common.bindings.EncoderParameterBankBinding;
import com.yaeltex.common.controls.RgbButton;
import com.yaeltex.common.controls.RingEncoder;
import com.yaeltex.common.remotes.OffsetRemotesGroup;

public class DjControllerExtension extends ControllerExtension {
    
    public static final YaeltexButtonLedState[] PARAM_COLORS = {
        YaeltexButtonLedState.RED,
        YaeltexButtonLedState.ORANGE,
        YaeltexButtonLedState.YELLOW,
        YaeltexButtonLedState.GREEN,
        YaeltexButtonLedState.AQUA,
        YaeltexButtonLedState.BLUE,
        YaeltexButtonLedState.PURPLE,
        YaeltexButtonLedState.WHITE,
    };
    private static ControllerHost debugHost;
    private Layer mainLayer;
    private HardwareSurface surface;
    private OffsetRemotesGroup projectGroup;
    private SequencerLayer sequencerLayer;
    
    public static void println(final String format, final Object... args) {
        if (debugHost != null) {
            debugHost.println(format.formatted(args));
        }
    }
    
    protected DjControllerExtension(final DjControllerExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }
    
    @Override
    public void init() {
        debugHost = getHost();
        final Context diContext = new Context(this);
        final YaeltexMidiProcessor midiProcessor = new YaeltexMidiProcessor(getHost(), 2);
        diContext.registerService(YaeltexMidiProcessor.class, midiProcessor);
        mainLayer = diContext.createLayer("MAIN_LAYER");
        surface = diContext.getService(HardwareSurface.class);
        sequencerLayer = diContext.getService(SequencerLayer.class);
        final RhdjViewControl viewControl = diContext.getService(RhdjViewControl.class);
        final Track rootTrack = viewControl.getRootTrack();
        
        projectGroup = new OffsetRemotesGroup("PROJECT",
            i -> rootTrack.createCursorRemoteControlsPage("PROJECT_%d".formatted(i + 1), 8, null), 11);
        
        assignRemotes(diContext);
        mainLayer.activate();
        sequencerLayer.activate();
        diContext.activate();
        midiProcessor.start();
    }
    
    private void assignRemotes(final Context diContext) {
        final HardwareElements hwElements = diContext.getService(HardwareElements.class);
        final RhdjViewControl viewControl = diContext.getService(RhdjViewControl.class);
        
        bindControls(hwElements.getSliders1(), 0, 0);
        bindControls(hwElements.getSmallKnobs1(), 1, 8);
        bindControls(hwElements.getSmallKnobs1(), 2, 0);
        bindControls(hwElements.getLargeKnobs1(), 3, 4);
        bindEncoders(hwElements.getBottomEncoders1(), 4, 0);
        bindControls(hwElements.getLargeKnobs2(), 5, 0, 4);
        bindControls(hwElements.getLargeKnobs2(), 6, 4);
        bindControls(hwElements.getSmallKnobs2(), 7, 8);
        bindControls(hwElements.getSmallKnobs2(), 8, 0);
        bindControls(hwElements.getSliders2(), 9, 0);
        bindDrumSends(hwElements.getStepButtons2(), 1, viewControl.getDrumPadBank(), 0);
        bindDrumSends(hwElements.getStepButtons2(), 0, viewControl.getDrumPadBank(), 1);
        
        final RgbButton holdPspButton = hwElements.getBottomEncoders2()[0].getButton();
        final RemoteControl holdParameter = projectGroup.getRemotes(10).getParameter(0);
        holdPspButton.bindToggleValue(mainLayer, holdParameter, YaeltexButtonLedState.RED);
    }
    
    private void bindControls(final AbsoluteHardwareControl[] controls, final int page, final int controlOffset) {
        bindControls(controls, page, controlOffset, 8);
    }
    
    private void bindControls(final AbsoluteHardwareControl[] controls, final int page, final int controlOffset,
        final int size) {
        final CursorRemoteControlsPage remotes = projectGroup.getRemotes(page);
        for (int i = 0; i < size; i++) {
            mainLayer.bind(controls[i + controlOffset], remotes.getParameter(i));
        }
    }
    
    private void bindDrumSends(final RgbButton[] buttons, final int row, final DrumPadBank drumPadBank,
        final int sendsIndex) {
        for (int i = 0; i < 8; i++) {
            final RgbButton button = buttons[i + row * 8];
            final DrumPad pad = drumPadBank.getItemAt(i);
            final Send send = pad.sendBank().getItemAt(sendsIndex);
            button.bindToggleValue(mainLayer, send, YaeltexButtonLedState.BLUE);
        }
    }
    
    private void bindEncoders(final RingEncoder[] ringEncoders, final int page, final int paramOffset) {
        final CursorRemoteControlsPage remotes = projectGroup.getRemotes(page);
        final BooleanValueObject pageInfo = projectGroup.getPageExists(page);
        for (int i = 0; i < ringEncoders.length; i++) {
            final RingEncoder encoder = ringEncoders[i];
            final RemoteControl parameter = remotes.getParameter(i + paramOffset);
            final YaeltexButtonLedState paramColor = PARAM_COLORS[i];
            parameter.exists().markInterested();
            mainLayer.addBinding(new EncoderParameterBankBinding(encoder, parameter.value(), pageInfo));
            encoder.bindLight(mainLayer, () -> pageInfo.get() ? paramColor : YaeltexButtonLedState.OFF);
            final RemoteControl pushParameter = remotes.getParameter(i + paramOffset + 4);
            final RgbButton button = encoder.getButton();
            button.bindToggleValue(mainLayer, pushParameter, PARAM_COLORS[i + 4]);
        }
        
    }
    
    public void exit() {
        
    }
    
    @Override
    public void flush() {
        surface.updateHardware();
    }
    
}
