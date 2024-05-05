package com.yaeltex.common;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceMatcher;
import com.bitwig.extension.controller.api.DrumPadBank;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;

public class AbstractViewControl {
    
    private final TrackBank trackBank;
    private final Track rootTrack;
    private final CursorTrack cursorTrack;
    private final PinnableCursorDevice cursorDevice;
    private final DrumPadBank drumPadBank;
    private final PinnableCursorClip cursorClip;
    private final CursorTrack drumCursorTrack;
    private int cursorTrackPosition = -1;
    private final PinnableCursorDevice drumDevice;
    private boolean onDrumTrack;
    
    public AbstractViewControl(final ControllerHost host, final int numScenes, final int numTracks, final int numSends,
        final int numDrumPads, final int numSteps, final boolean flatTrackList) {
        rootTrack = host.getProject().getRootTrackGroup();
        trackBank = host.createTrackBank(numTracks, numSends, numScenes, flatTrackList);
        cursorTrack = host.createCursorTrack(numSends, numScenes);
        trackBank.followCursorTrack(cursorTrack);
        cursorTrack.exists().markInterested();
        cursorTrack.name().markInterested();
        cursorDevice = cursorTrack.createCursorDevice();
        
        drumCursorTrack = host.createCursorTrack("drum", "drumtrack", 2, numScenes, false);
        drumDevice = drumCursorTrack.createCursorDevice("drumdetection", "Pad Device", numSends,
            CursorDeviceFollowMode.FIRST_INSTRUMENT);
        drumCursorTrack.isPinned().markInterested();
        
        cursorClip = drumCursorTrack.createLauncherCursorClip("SQClip", "SQClip", numSteps, 1);
        
        final DeviceMatcher drumMatcher =
            host.createBitwigDeviceMatcher(com.bitwig.extensions.framework.values.SpecialDevices.DRUM.getUuid());
        
        
        final DeviceBank drumBank = cursorTrack.createDeviceBank(1);
        drumBank.setDeviceMatcher(drumMatcher);
        final Device drumDeviceFollow = drumBank.getDevice(0);
        drumDeviceFollow.exists().addValueObserver(onDrumDevice -> {
            this.onDrumTrack = onDrumDevice;
            if (cursorTrackPosition != -1 && onDrumDevice) {
                drumCursorTrack.selectChannel(cursorTrack);
                drumCursorTrack.isPinned().set(true);
            }
        });
        cursorTrack.position().addValueObserver(position -> {
            this.cursorTrackPosition = position;
            if (cursorTrackPosition != -1 && onDrumTrack) {
                drumCursorTrack.selectChannel(cursorTrack);
                drumCursorTrack.isPinned().set(true);
            }
        });
        
        drumPadBank = drumDevice.createDrumPadBank(8);
    }
    
    public Track getRootTrack() {
        return rootTrack;
    }
    
    public CursorTrack getCursorTrack() {
        return cursorTrack;
    }
    
    public PinnableCursorDevice getCursorDevice() {
        return cursorDevice;
    }
    
    public PinnableCursorClip getCursorClip() {
        return cursorClip;
    }
    
    public DrumPadBank getDrumPadBank() {
        return drumPadBank;
    }
    
    public TrackBank getTrackBank() {
        return trackBank;
    }
    
    public CursorTrack getDrumCursorTrack() {
        return drumCursorTrack;
    }
}
