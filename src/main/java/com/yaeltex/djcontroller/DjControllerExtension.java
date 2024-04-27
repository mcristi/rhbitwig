package com.yaeltex.djcontroller;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.di.Context;
import com.yaeltex.common.YaeltexMidiProcessor;

public class DjControllerExtension extends ControllerExtension {
    
    private static ControllerHost debugHost;
    private Layer mainLayer;
    private HardwareSurface surface;
    
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
        mainLayer.activate();
        diContext.activate();
        midiProcessor.start();
    }
    
    public void exit() {
    
    }
    
    @Override
    public void flush() {
        surface.updateHardware();
    }
    
}
