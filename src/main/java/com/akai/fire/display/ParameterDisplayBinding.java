package com.akai.fire.display;

import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extensions.framework.Binding;

public class ParameterDisplayBinding extends Binding<Parameter, DisplayTarget> {

	private double rawValue;
	private String displayValue;
	private final int index;
	private final int typeIndex;
	private final boolean bipolar;
    private double discreteAccumulator = 0.0;

	public ParameterDisplayBinding(final int typeIndex, final int index, final Parameter source,
			final DisplayTarget target, final boolean bipolar) {
		super(target, source, target);
		this.index = index;
		this.typeIndex = typeIndex;
		this.bipolar = bipolar;
		source.value().addValueObserver(this::handleRawValue);
		source.displayedValue().addValueObserver(this::handleDisplayValue);
		source.discreteValueCount().markInterested();
	}

	private void handleRawValue(final double rawValue) {
		this.rawValue = rawValue;
		if (isActive()) {
			getTarget().update(index, typeIndex, rawValue, displayValue, bipolar);
		}
	}

	private void handleDisplayValue(final String displayValue) {
		this.displayValue = displayValue;
		if (isActive()) {
			getTarget().update(index, typeIndex, rawValue, displayValue, bipolar);
		}
	}

	@Override
	protected void deactivate() {
	}

	@Override
	protected void activate() {
		update();
	}

	public void modify(final double inc) {
		final SettableRangedValue value = getSource().value();

        // Check if this is a discrete parameter by checking if it has enumerated values
        if (getSource().discreteValueCount().get() > 0) {
            final boolean isShiftMode = Math.abs(inc) <= 0.005; // SHIFT_INC is 0.001
            discreteAccumulator += inc;
            final double stepThreshold = isShiftMode ? 0.03 : 0.3;

            if (Math.abs(discreteAccumulator) >= stepThreshold) {
                if (discreteAccumulator > 0) {
                    getSource().inc(1);
                    discreteAccumulator -= stepThreshold;
                } else {
                    getSource().inc(-1);
                    discreteAccumulator += stepThreshold;
                }
            }
        } else {
            // For continuous parameters, use the existing logic
            final double preValue = value.get();
            final double newValue = Math.min(1, Math.max(0, preValue + inc));
            if (preValue != newValue) {
                value.setImmediately(newValue);
            }
        }
	}

    public void increase(final int inc) {
        getSource().inc(inc);
    }

	public double getValue() {
		return getSource().value().get();
	}

	public void update() {
		getTarget().update(index, typeIndex, rawValue, displayValue, bipolar);
	}

}
