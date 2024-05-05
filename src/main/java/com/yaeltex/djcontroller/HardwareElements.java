package com.yaeltex.djcontroller;

import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.HardwareSlider;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MultiStateHardwareLight;
import com.bitwig.extensions.framework.di.Component;
import com.yaeltex.common.YaeltexButtonLedState;
import com.yaeltex.common.YaeltexMidiProcessor;
import com.yaeltex.common.controls.RgbButton;
import com.yaeltex.common.controls.RingEncoder;

@Component
public class HardwareElements {
    
    private final RingEncoder[] bottomEncoders1 = new RingEncoder[4];
    private final RingEncoder[] bottomEncoders2 = new RingEncoder[4];
    private final HardwareSlider[] sliders1 = new HardwareSlider[8];
    private final HardwareSlider[] sliders2 = new HardwareSlider[8];
    private final RgbButton[] stepButtons1 = new RgbButton[16];
    private final RgbButton[] stepButtons2 = new RgbButton[16];
    private final RgbButton[] selectButtons1 = new RgbButton[16];
    private final RgbButton[] selectButtons2 = new RgbButton[16];
    private final RgbButton[] mainButtons1 = new RgbButton[8];
    private final RgbButton[] mainButtons2 = new RgbButton[8];
    private final AbsoluteHardwareKnob[] smallKnobs1 = new AbsoluteHardwareKnob[16];
    private final AbsoluteHardwareKnob[] smallKnobs2 = new AbsoluteHardwareKnob[16];
    private final AbsoluteHardwareKnob[] largeKnobs1 = new AbsoluteHardwareKnob[12];
    private final AbsoluteHardwareKnob[] largeKnobs2 = new AbsoluteHardwareKnob[12];
    
    public HardwareElements(final ControllerHost host, final HardwareSurface surface,
        final YaeltexMidiProcessor midiProcessor) {
        surface.setPhysicalSize(430, 330);
        
        for (int i = 0; i < 4; i++) {
            bottomEncoders1[i] = new RingEncoder(0, 0x4 + i, 0, "ENC_1_%d".formatted(i + 1), surface, midiProcessor,
                RingEncoder.Mode.SIGNED_BIT);
            bottomEncoders2[i] = new RingEncoder(0, 0x4 + i, 1, "ENC_2_%d".formatted(i + 1), surface, midiProcessor,
                RingEncoder.Mode.SIGNED_BIT);
        }
        
        for (int i = 0; i < 8; i++) {
            sliders1[i] = createSliderCc("SILDER_DR_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(0), 8 + i);
            sliders2[i] = createSliderCc("SILDER_SYN_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(1), 8 + i);
            mainButtons1[i] = new RgbButton(0, 0, 40 + i, "MAIN_BUTTON_1_%d".formatted(i + 1), surface, midiProcessor);
            mainButtons2[i] = new RgbButton(1, 0, 40 + i, "MAIN_BUTTON_2_%d".formatted(i + 1), surface, midiProcessor);
        }
        for (int i = 0; i < 16; i++) {
            stepButtons1[i] =
                new RgbButton(0, 0, 8 + i, "PATTERN_BUTTON_1_%d".formatted(i + 1), surface, midiProcessor);
            stepButtons2[i] =
                new RgbButton(1, 0, 8 + i, "PATTERN_BUTTON_2_%d".formatted(i + 1), surface, midiProcessor);
            selectButtons1[i] = new RgbButton(0, 0, 24 + i, "SEL_BUTTON_1_%d".formatted(i + 1), surface, midiProcessor);
            selectButtons2[i] = new RgbButton(1, 0, 24 + i, "SEL_BUTTON_2_%d".formatted(i + 1), surface, midiProcessor);
            smallKnobs1[i] = createKnob("SMALL_ENC_1_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(0), 16 + i);
            smallKnobs2[i] = createKnob("SMALL_ENC_2_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(1), 16 + i);
        }
        for (int i = 0; i < 12; i++) {
            final int ccNr = (2 - i / 4) * 4 + i % 4 + 32;
            largeKnobs1[i] = createKnob("LARGE_ENC_1_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(0), ccNr);
            largeKnobs2[i] = createKnob("LARGE_ENC_2_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(1), ccNr);
        }
    }
    
    public HardwareSlider[] getSliders1() {
        return sliders1;
    }
    
    public HardwareSlider[] getSliders2() {
        return sliders2;
    }
    
    public AbsoluteHardwareKnob[] getLargeKnobs1() {
        return largeKnobs1;
    }
    
    public AbsoluteHardwareKnob[] getLargeKnobs2() {
        return largeKnobs2;
    }
    
    public RingEncoder[] getBottomEncoders1() {
        return bottomEncoders1;
    }
    
    public RingEncoder[] getBottomEncoders2() {
        return bottomEncoders2;
    }
    
    public AbsoluteHardwareKnob[] getSmallKnobs1() {
        return smallKnobs1;
    }
    
    public AbsoluteHardwareKnob[] getSmallKnobs2() {
        return smallKnobs2;
    }
    
    public RgbButton[] getStepButtons1() {
        return stepButtons1;
    }
    
    public RgbButton[] getStepButtons2() {
        return stepButtons2;
    }
    
    public RgbButton[] getMainButtons1() {
        return mainButtons1;
    }
    
    public RgbButton[] getSelectButtons1() {
        return selectButtons1;
    }
    
    private HardwareSlider createSliderCc(final String name, final HardwareSurface surface, final MidiIn midiIn,
        final int ccNr) {
        final HardwareSlider fader = surface.createHardwareSlider(name);
        fader.setAdjustValueMatcher(midiIn.createAbsoluteCCValueMatcher(0, ccNr));
        return fader;
    }
    
    private AbsoluteHardwareKnob createKnob(final String name, final HardwareSurface surface, final MidiIn midiIn,
        final int ccNr) {
        final AbsoluteHardwareKnob knob = surface.createAbsoluteHardwareKnob(name);
        knob.setAdjustValueMatcher(midiIn.createAbsoluteCCValueMatcher(0, ccNr));
        final MultiStateHardwareLight knobLight = surface.createMultiStateHardwareLight(name + "_LIGHT");
        knob.setBackgroundLight(knobLight);
        knobLight.state().setValue(YaeltexButtonLedState.of(60));
        return knob;
    }
}
