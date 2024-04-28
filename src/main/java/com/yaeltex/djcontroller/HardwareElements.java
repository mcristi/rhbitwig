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
    
    private final RingEncoder seqLoopEncoder;
    private final RingEncoder tempoMinEncoder;
    private final RingEncoder tempoMaxEncoder;
    private final RingEncoder synthTypeEncoder;
    private final RingEncoder chordEncoder;
    private final RingEncoder folderEncoder;
    private final RingEncoder songsEncoder;
    private final RingEncoder drumEncoder;
    private final RingEncoder[] decEncoders = new RingEncoder[4];
    private final RingEncoder[] loopEncoders = new RingEncoder[4];
    private final HardwareSlider[] sliders1 = new HardwareSlider[8];
    private final HardwareSlider[] sliders2 = new HardwareSlider[8];
    private final RgbButton[] patternButton1 = new RgbButton[16];
    private final RgbButton[] patternButton2 = new RgbButton[16];
    private final RgbButton[] selectButton1 = new RgbButton[16];
    private final RgbButton[] selectButton2 = new RgbButton[16];
    private final RgbButton[] mainButton1 = new RgbButton[8];
    private final RgbButton[] mainButton2 = new RgbButton[8];
    private final AbsoluteHardwareKnob[] smallEncoders1 = new AbsoluteHardwareKnob[16];
    private final AbsoluteHardwareKnob[] smallEncoders2 = new AbsoluteHardwareKnob[16];
    private final AbsoluteHardwareKnob[] largeEncoders1 = new AbsoluteHardwareKnob[12];
    private final AbsoluteHardwareKnob[] largeEncoders2 = new AbsoluteHardwareKnob[12];
    
    public HardwareElements(final ControllerHost host, final HardwareSurface surface,
        final YaeltexMidiProcessor midiProcessor) {
        surface.setPhysicalSize(430, 330);
        
        for (int i = 0; i < 4; i++) {
            decEncoders[i] = new RingEncoder(0, 0x4 + i, 0, "DECK_%d".formatted(i + 1), surface, midiProcessor,
                RingEncoder.Mode.SIGNED_BIT);
            loopEncoders[i] = new RingEncoder(0, 0x4 + i, 1, "LOOP_%d".formatted(i + 1), surface, midiProcessor,
                RingEncoder.Mode.SIGNED_BIT);
        }
        
        for (int i = 0; i < 8; i++) {
            sliders1[i] = createSliderCc("SILDER_DR_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(0), 40 + i);
            sliders2[i] = createSliderCc("SILDER_SYN_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(1), 40 + i);
            mainButton1[i] = new RgbButton(0, 0, 0x32 + i, "MAIN_BUTTON_1_%d".formatted(i + 1), surface, midiProcessor);
            mainButton2[i] = new RgbButton(1, 0, 0x32 + i, "MAIN_BUTTON_2_%d".formatted(i + 1), surface, midiProcessor);
        }
        for (int i = 0; i < 16; i++) {
            patternButton1[i] =
                new RgbButton(0, 0, 0x14 + i, "PATTERN_BUTTON_1_%d".formatted(i + 1), surface, midiProcessor);
            patternButton2[i] =
                new RgbButton(1, 0, 0x14 + i, "PATTERN_BUTTON_2_%d".formatted(i + 1), surface, midiProcessor);
            selectButton1[i] =
                new RgbButton(0, 0, 0x28 + i, "SEL_BUTTON_1_%d".formatted(i + 1), surface, midiProcessor);
            selectButton2[i] =
                new RgbButton(1, 0, 0x28 + i, "SEL_BUTTON_2_%d".formatted(i + 1), surface, midiProcessor);
            smallEncoders1[i] =
                createKnob("SMALL_ENC_1_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(0), 24 + i);
            smallEncoders2[i] =
                createKnob("SMALL_ENC_2_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(1), 24 + i);
        }
        for (int i = 0; i < 12; i++) {
            largeEncoders1[i] =
                createKnob("LARGE_ENC_1_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(0), 12 + i);
            largeEncoders1[i] =
                createKnob("LARGE_ENC_2_%d".formatted(i + 1), surface, midiProcessor.getMidiIn(1), 12 + i);
        }
        
        seqLoopEncoder = new RingEncoder(0, 0x8, 0, "SEQ_LOOP", surface, midiProcessor, RingEncoder.Mode.SIGNED_BIT);
        tempoMinEncoder = new RingEncoder(0, 0x9, 0, "TEMPO_MIN", surface, midiProcessor, RingEncoder.Mode.SIGNED_BIT);
        tempoMaxEncoder = new RingEncoder(0, 0xA, 0, "TEMPO_MAX", surface, midiProcessor, RingEncoder.Mode.SIGNED_BIT);
        synthTypeEncoder =
            new RingEncoder(0, 0x8, 0, "SYNTH_TYPE", surface, midiProcessor, RingEncoder.Mode.SIGNED_BIT);
        chordEncoder = new RingEncoder(0, 0x8, 1, "CHORD_ENCODER", surface, midiProcessor, RingEncoder.Mode.SIGNED_BIT);
        folderEncoder =
            new RingEncoder(0, 0x9, 1, "FOLDER_ENCODER", surface, midiProcessor, RingEncoder.Mode.SIGNED_BIT);
        songsEncoder = new RingEncoder(0, 0xA, 1, "SONGS_ENCODER", surface, midiProcessor, RingEncoder.Mode.SIGNED_BIT);
        drumEncoder = new RingEncoder(0, 0xB, 1, "DRUMS_ENCODER", surface, midiProcessor, RingEncoder.Mode.SIGNED_BIT);
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
