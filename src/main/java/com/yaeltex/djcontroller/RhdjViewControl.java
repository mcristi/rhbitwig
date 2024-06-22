package com.yaeltex.djcontroller;

import java.util.ArrayList;
import java.util.List;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extensions.framework.di.Component;
import com.rhcommons.InKeyScale;
import com.rhcommons.MapTranspose;
import com.yaeltex.common.AbstractViewControl;

@Component
public class RhdjViewControl extends AbstractViewControl {
    private static final int NUM_SCENES = 4;
    private static final int NUM_TRACKS = 8;
    private static final int NUM_SENDS = 4;
    private static final int DRUM_PADS = 8;
    private static final int CLIP_STEPS = 16;
    private final List<MapTranspose> transposeDevices = new ArrayList<>();
    
    public RhdjViewControl(final ControllerHost host) {
        super(host, NUM_SCENES, NUM_TRACKS, NUM_SENDS, DRUM_PADS, CLIP_STEPS, true);
        final TrackBank deviceOverviewTrackBank = host.createTrackBank(16, 1, 1);
        for (int i = 0; i < 16; i++) {
            final int index = i;
            final Track track = deviceOverviewTrackBank.getItemAt(i);
            final CursorDevice cursorDevice = track.createCursorDevice();
            transposeDevices.add(new MapTranspose(index, host, track));
        }
    }
    
    public void setScale(final String scaleValue) {
        final int n = scaleValue.length();
        final String type = scaleValue.substring(n - 1, n);
        final int number = Integer.parseInt(scaleValue.substring(0, n - 1)) - 1;
        final int baseNote = (6 * 12 + 11 - ("A".equals(type) ? 3 : 0) - number * 5) % 12;
        
        transposeDevices.stream().filter(device -> device.exists()).forEach(device -> {
            if ("A".equals(type)) {
                device.setScale(InKeyScale.MINOR);
                device.setRootNote(baseNote);
            } else {
                device.setScale(InKeyScale.MAJOR);
                device.setRootNote(baseNote);
            }
        });
    }
    
    
}
