package com.yaeltex.seqarp168mk2;

import java.util.ArrayList;
import java.util.List;

import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.InternalHardwareLightState;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.Layers;
import com.bitwig.extensions.framework.di.Component;
import com.bitwig.extensions.framework.values.BooleanValueObject;
import com.bitwig.extensions.framework.values.IntValueObject;
import com.yaeltex.common.YaeltexButtonLedState;
import com.yaeltex.common.YaeltexMidiProcessor;
import com.yaeltex.common.bindings.EncoderParameterBankBinding;
import com.yaeltex.common.controls.RgbButton;
import com.yaeltex.common.controls.RingEncoder;
import com.yaeltex.common.remotes.IndexedRemotesGroup;

@Component
public class PerformanceLayer extends Layer {
    private static final int LAUNCHER_TRACK_RANGE = 64;
    private static final YaeltexButtonLedState ACTIVE_COLOR = YaeltexButtonLedState.of(126);
    private final YaeltexButtonLedState[] slotColors = new YaeltexButtonLedState[16];
    private final BitwigViewControl viewControl;
    private final YaeltexMidiProcessor midiProcessor;
    private final TrackBank clipLauncherBank;
    private final Layer modifier1Layer;
    private int clipLaunchIndex = 0;
    private final SettableRangedValue clipViewOffset;
    private final ControllerHost host;
    private boolean altState;
    
    public PerformanceLayer(final Layers layers, final SeqArpHardwareElements hwElements,
        final BitwigViewControl viewControl, final ControllerHost host, final YaeltexMidiProcessor midiProcessor) {
        super(layers, "PERFORMANCE_LAYER");
        final DocumentState documentState = host.getDocumentState();
        this.midiProcessor = midiProcessor;
        this.modifier1Layer = new Layer(layers, "PERFORMANCE_MODIFIER_LAYER");
        final TrackBank mixTrackBank = host.createTrackBank(16, 1, 1, true);
        clipLauncherBank = host.createTrackBank(4, 1, 4, true);
        
        final Clip clip = host.createLauncherCursorClip(1, 1);
        
        this.host = host;
        setUpTrackRow(hwElements, mixTrackBank);
        setUpClipLaunching(hwElements);
        final List<IntValueObject> pageReferences = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final SettableRangedValue pageOffset =
                documentState.getNumberSetting("Row %d".formatted(i + 1), "Peformance Page", 1, 32, 1, "", (i + 1));
            final IntValueObject indexValue = new IntValueObject(i, 0, 32);
            pageOffset.addValueObserver(32, v -> indexValue.set(v));
            pageReferences.add(indexValue);
        }
        
        clipViewOffset =
            documentState.getNumberSetting("Performance Clip Launcher offset", "Clip Control", 1, LAUNCHER_TRACK_RANGE,
                1, "", 1);
        clipViewOffset.addValueObserver(LAUNCHER_TRACK_RANGE, newOffset -> {
            clipLauncherBank.scrollPosition().set(newOffset);
            clipLaunchIndex = newOffset;
        });
        
        this.viewControl = viewControl;
        final Track rootTrack = viewControl.getRootTrack();
        final IndexedRemotesGroup projectGroup = new IndexedRemotesGroup("PROJECT",
            i -> rootTrack.createCursorRemoteControlsPage("PERFORMANCE_%d".formatted(i + 1), 8, null), pageReferences);
        assignRemotes(hwElements, projectGroup, this);
    }
    
    private void setUpClipLaunching(final SeqArpHardwareElements hwElements) {
        for (int trackIndex = 0; trackIndex < 4; trackIndex++) {
            final Track track = clipLauncherBank.getItemAt(trackIndex);
            for (int sceneIndex = 0; sceneIndex < 4; sceneIndex++) {
                final int buttonIndex = trackIndex * 4 + sceneIndex;
                final ClipLauncherSlot slot = track.clipLauncherSlotBank().getItemAt(sceneIndex);
                prepareSlot(buttonIndex, slot);
                final RgbButton button = hwElements.getStepButton(buttonIndex);
                button.bindIsPressed(this, pressed -> handleClipPressed(pressed, slot, buttonIndex));
                button.bindLight(this, () -> getSlotColor(buttonIndex, slot));
            }
        }
    }
    
    private void setUpTrackRow(final SeqArpHardwareElements hwElements, final TrackBank mixTrackBank) {
        final YaeltexButtonLedState modColor = YaeltexButtonLedState.of(126);
        for (int i = 0; i < 16; i++) {
            final int trackIndex = i;
            final Track track = mixTrackBank.getItemAt(trackIndex);
            final RgbButton button = hwElements.getStepButton(16 + trackIndex);
            prepareTrack(track, trackIndex);
            button.bindPressed(this, () -> track.mute().toggle());
            button.bindLight(this, () -> trackLightState(track, trackIndex));
            button.bindPressed(modifier1Layer, () -> clipViewOffset.set(trackIndex, LAUNCHER_TRACK_RANGE));
            button.bindLight(
                modifier1Layer, () -> trackIndex == clipLaunchIndex ? YaeltexButtonLedState.RED : modColor);
        }
    }
    
    private void prepareTrack(final Track track, final int trackIndex) {
        track.mute().markInterested();
        track.exists().markInterested();
        track.isGroup().markInterested();
    }
    
    private void handleClipPressed(final boolean pressed, final ClipLauncherSlot slot, final int buttonIndex) {
        if (pressed) {
            if (altState) {
                slot.launchAlt();
            } else {
                slot.launch();
            }
        } else {
            if (altState) {
                slot.launchReleaseAlt();
            } else {
                slot.launchRelease();
            }
        }
    }
    
    private InternalHardwareLightState getSlotColor(final int index, final ClipLauncherSlot slot) {
        if (slot.hasContent().get()) {
            if (slot.isPlaybackQueued().get()) {
                return midiProcessor.blinkMid(slotColors[index]);
            } else if (slot.isStopQueued().get()) {
                return midiProcessor.blinkFast(slotColors[index]);
            } else if (slot.isPlaying().get()) {
                return YaeltexButtonLedState.GREEN;
            }
            return slotColors[index];
        }
        
        if (slot.isPlaybackQueued().get() || slot.isRecordingQueued().get()) {
            return midiProcessor.blinkMid(slotColors[index]);
        }
        
        return YaeltexButtonLedState.OFF;
    }
    
    private void prepareSlot(final int buttonIndex, final ClipLauncherSlot slot) {
        slot.exists().markInterested();
        slot.isPlaying().markInterested();
        slot.hasContent().markInterested();
        slot.isPlaybackQueued().markInterested();
        slot.isStopQueued().markInterested();
        slot.isRecordingQueued().markInterested();
        slot.color().addValueObserver((r, g, b) -> slotColors[buttonIndex] = YaeltexButtonLedState.of(r, g, b));
    }
    
    private YaeltexButtonLedState trackLightState(final Track track, final int trackIndex) {
        if (track.exists().get()) {
            return track.mute().get() ? YaeltexButtonLedState.ORANGE : YaeltexButtonLedState.WHITE;
        }
        return YaeltexButtonLedState.OFF;
    }
    
    private void assignRemotes(final SeqArpHardwareElements hwElements, final IndexedRemotesGroup trackGroup,
        final Layer layer) {
        for (int i = 0; i < 4; i++) {
            final int pageIndex = i;
            final CursorRemoteControlsPage remotes = trackGroup.getRemotes(i);
            final BooleanValueObject pageInfo = trackGroup.getPageExists(i);
            for (int j = 0; j < 8; j++) {
                final int remoteIndex = j;
                final RingEncoder encoder = hwElements.getEncoder(remoteIndex + (3 - pageIndex) * 8);
                final RemoteControl parameter = remotes.getParameter(remoteIndex);
                final YaeltexButtonLedState paramColor = RemotesLayer.PARAM_COLORS[remoteIndex];
                parameter.exists().markInterested();
                layer.addBinding(new EncoderParameterBankBinding(encoder, parameter.value(), pageInfo));
                encoder.bindLight(layer, () -> pageInfo.get() ? paramColor : YaeltexButtonLedState.OFF);
                encoder.getButton().bindPressed(modifier1Layer, () -> trackGroup.selectPage(pageIndex, remoteIndex));
                encoder.getButton().bindLight(modifier1Layer, () -> trackGroup.isActive(pageIndex, remoteIndex)
                    ? YaeltexButtonLedState.WHITE
                    : YaeltexButtonLedState.OFF);
            }
        }
    }
    
    @Override
    protected void onActivate() {
        super.onActivate();
        clipLauncherBank.setShouldShowClipLauncherFeedback(true);
    }
    
    @Override
    protected void onDeactivate() {
        super.onDeactivate();
        clipLauncherBank.setShouldShowClipLauncherFeedback(false);
        modifier1Layer.setIsActive(false);
    }
    
    public void setModifier1LayerActive(final boolean active) {
        if (this.isActive()) {
            modifier1Layer.setIsActive(active);
        }
    }
    
    public boolean isModifierActive() {
        return this.modifier1Layer.isActive();
    }
    
    public boolean isAltState() {
        return this.altState;
    }
    
    public void setAltState(final boolean active) {
        if (active) {
            this.altState = !this.altState;
        }
    }
}