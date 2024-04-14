package com.yaeltex.seqarp168mk2.arpcontrol;

import com.bitwig.extensions.framework.values.ValueObject;

public abstract class DeviceConfig {
    
    private final ControlMode mode;
    protected StepEncoderMode stepEncoderMode = StepEncoderMode.DEFAULT;
    protected final ValueObject<FocusDeviceMode> deviceFocus;
    protected boolean active;
    
    public DeviceConfig(final ControlMode mode, final ValueObject<FocusDeviceMode> deviceFocus) {
        this.deviceFocus = deviceFocus;
        this.mode = mode;
    }
    
    public abstract void setIsActive(final boolean active);
    
    public ControlMode getMode() {
        return mode;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public StepEncoderMode getStepEncoderMode() {
        return stepEncoderMode;
    }
    
    protected void changeDeviceFocus(final int which) {
        if (which == 1 && this.deviceFocus.get() == FocusDeviceMode.DEVICE_1) {
            deviceFocus.set(FocusDeviceMode.DEVICE_2);
        } else if (which == 0 && this.deviceFocus.get() == FocusDeviceMode.DEVICE_2) {
            deviceFocus.set(FocusDeviceMode.DEVICE_1);
        }
    }
    
    
    public void changeEncoderMode(final boolean pressed) {
    }
}
