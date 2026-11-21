package com.akai.fire;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.DrumPadBank;
import com.bitwig.extension.controller.api.PinnableCursorDevice;

public class ViewCursorControl {
	private final CursorTrack cursorTrack;
	private final PinnableCursorDevice primaryDevice;
	private final DrumPadBank drumPadBank;
	// all pads of the Drum Machine, to know what lies on the pages the controller is not showing
	private final DrumPadBank allDrumPads;

	public ViewCursorControl(final ControllerHost host, final int scenes) {
		super();

		this.cursorTrack = host.createCursorTrack(8, scenes);

		primaryDevice = cursorTrack.createCursorDevice("drumdetection", "Pad Device", 8,
				CursorDeviceFollowMode.FIRST_INSTRUMENT);
		primaryDevice.hasDrumPads().markInterested();
		primaryDevice.exists().markInterested();
		drumPadBank = primaryDevice.createDrumPadBank(16);
		allDrumPads = primaryDevice.createDrumPadBank(128);
		// only for looking: without this Bitwig marks every pad as controlled
		allDrumPads.setIndication(false);
	}

	public CursorTrack getCursorTrack() {
		return cursorTrack;
	}

	public PinnableCursorDevice getPrimaryDevice() {
		return primaryDevice;
	}

	public DrumPadBank getAllDrumPads() {
		return allDrumPads;
	}

	public DrumPadBank getDrumPadBank() {
		return drumPadBank;
	}

}
