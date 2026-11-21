package com.akai.fire;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class DrumSequencerDefinition extends ControllerExtensionDefinition {
    private static final UUID DRIVER_ID = UUID.fromString("24d0db9d-1951-406f-bdcf-d42c32d3d394");

    public DrumSequencerDefinition() {
    }

    @Override
    public String getName() {
        return "Drum Sequencer";
    }

    @Override
    public String getAuthor() {
        return "R.Hawtin/E.Ahrens/mcristi";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public UUID getId() {
        return DRIVER_ID;
    }

    @Override
    public String getHardwareVendor() {
        return "Akai";
    }

    @Override
    public String getHardwareModel() {
        return "Fire + MIDIMix";
    }

    @Override
    public String getHelpFilePath() {
        return "Controllers/Akai/Hawtin AKAI Fire.pdf";
    }

    @Override
    public String getSupportFolderPath() {
        return "";
    }

    @Override
    public int getRequiredAPIVersion() {
        return 20;
    }

    @Override
    public int getNumMidiInPorts() {
        // port 0: Akai Fire, port 1: Akai MIDI Mix
        return 2;
    }

    @Override
    public int getNumMidiOutPorts() {
        return 2;
    }

    @Override
    public void listAutoDetectionMidiPortNames(final AutoDetectionMidiPortNamesList list,
                                               final PlatformType platformType) {
        final String midiMix = platformType == PlatformType.LINUX ? "MIDI Mix MIDI 1" : "MIDI Mix";
        list.add(new String[]{"FL STUDIO FIRE", midiMix}, new String[]{"FL STUDIO FIRE", midiMix});
    }

    @Override
    public DrumSequencerExtension createInstance(final ControllerHost host) {
        return new DrumSequencerExtension(this, host);
    }
}
