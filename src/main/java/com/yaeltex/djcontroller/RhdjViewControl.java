package com.yaeltex.djcontroller;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extensions.framework.di.Component;
import com.yaeltex.common.AbstractViewControl;

@Component
public class RhdjViewControl extends AbstractViewControl {
    private static final int NUM_SCENES = 4;
    private static final int NUM_TRACKS = 8;
    private static final int NUM_SENDS = 4;
    private static final int DRUM_PADS = 8;
    private static final int CLIP_STEPS = 16;
    
    public RhdjViewControl(final ControllerHost host) {
        super(host, NUM_SCENES, NUM_TRACKS, NUM_SENDS, DRUM_PADS, CLIP_STEPS, true);
    }
    
}
