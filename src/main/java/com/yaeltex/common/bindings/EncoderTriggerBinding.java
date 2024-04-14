package com.yaeltex.common.bindings;

import com.bitwig.extension.controller.api.RelativeHardwarControlBindable;
import com.bitwig.extensions.framework.Binding;
import com.yaeltex.common.controls.RingEncoder;

public class EncoderTriggerBinding extends Binding<RingEncoder, Runnable> {
    
    private final RelativeHardwarControlBindable incBinder;
    
    public EncoderTriggerBinding(final RingEncoder encoder, final Runnable action) {
        super(action, encoder, action);
        encoder.getEncoder().hasTargetValue().markInterested();
        incBinder = encoder.createIncrementBinder(incValue -> action.run());
    }
    
    @Override
    protected void deactivate() {
        getSource().getEncoder().clearBindings();
    }
    
    @Override
    protected void activate() {
        getSource().getEncoder().addBinding(incBinder);
    }
}
