package com.yaeltex.common.bindings;

import com.bitwig.extension.controller.api.RelativeHardwarControlBindable;
import com.bitwig.extensions.framework.Binding;
import com.yaeltex.common.IntValueObject;
import com.yaeltex.common.controls.RingEncoder;

public class EncoderIntShortRangeBinding extends Binding<RingEncoder, IntValueObject> {
    
    private final int value;
    private final RelativeHardwarControlBindable incBinder;
    private final int[] valueMapping;
    private final int changeSteps;
    private int accumulation = 0;
    
    public EncoderIntShortRangeBinding(final RingEncoder encoder, final IntValueObject target, final int changeSteps,
        final int[] valueMapping) {
        super(encoder, target);
        encoder.getEncoder().hasTargetValue().markInterested();
        this.valueMapping = valueMapping;
        this.changeSteps = changeSteps;
        
        this.value = target.getValue();
        incBinder = encoder.createIncrementBinder(this::handleIncrement);
        target.addValueObserver(newValue -> {
            if (isActive()) {
                encoder.updateValue(valueMapping[target.getValue()]);
            }
        });
    }
    
    private void handleIncrement(final int inc) {
        accumulation += inc;
        if (Math.abs(accumulation) > this.changeSteps) {
            getTarget().increment(inc);
            accumulation = 0;
        }
    }
    
    @Override
    protected void deactivate() {
        getSource().getEncoder().clearBindings();
    }
    
    @Override
    protected void activate() {
        getSource().getEncoder().addBinding(incBinder);
        getSource().updateValue(valueMapping[value]);
    }
}
