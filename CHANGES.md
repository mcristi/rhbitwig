## Akai Fire differences from upstream

### Pads
- Shift + Pad ⇒ cycle and apply color from a predefined color list 
- The Pattern up / down LEDs are lit only if the page in that direction has at least one pad with a device on it (the buttons still work on empty pages)
- Pattern up / down shows the keys of the new pad page on the display (e.g. E2 - G3) and how many of its pads are in use
- Pressing an empty pad of the Drum Machine sets it up for a synth or sample: a Note Transpose followed by an empty Komplete Kontrol (VST3), with a random color from the Shift + Pad list and named after its key (e.g. A#1). The Note Transpose moves the pad's key to C3 (e.g. +14 semitones on A#1), so that samples play at their original pitch
- Shift + Clip ⇒ cycle and apply color from a predefined color list
- Pressing a clip pad (new clip, or Copy + clip) whose scene does not exist yet adds the missing scenes to the project first
- Shift + Last step => duplicate clip content
- Shift + Copy + Clip ⇒ copy only the selected pad's pattern (visible page, with all note values) into that clip, onto the same pad. An empty slot gets a clip of the same length first
- Step colors reflect the note velocity: pad color at the normal velocity, fading to a darker, grayish color for lower velocities and to a darker, more saturated color up to the accent velocity
- New clip action (on pressing empty pad) copies the previous clip to preserve automations
- Delete clip is the default behavior (cursorClip.clearSteps() deletes the automations as well) 

### Buttons
- Accent value is reflected on the note repeat
- Metronome is mapped to metronome button
- Pin track instead of pin clip
- Start: continue play instead of start
- Shift + Select mode: toggle normal / alternative mode
- While Shift alone is held (not with Select, Copy, Delete or Last Step) the step pads show which steps have operators: orange = chance, yellow = repeats, green = recurrence, blue = an "if" condition (e.g. Fill on), red = more than one; notes without operators are white, the playhead has the pad's color
- Shift + Grid left / right: previous / next page of 32 steps, for clips longer than 32 steps (Grid left / right alone moves the notes of the visible page)
- Holding steps and touching an encoder or pressing the mode button keeps the steps on release

### Encoders
- New mode: User 2 - first 4 macros from the first device in chain
- New alternate mode: User 2 - last 4 macros from the first device in chain
- Device param names are reflected on the screen
- Repeats are shown like in Bitwig (Off, 2, 3, ... 16). 
- While a step is held and an encoder is used, the second pad row shows the value, colored by encoder like Bitwig's remote controls. It stays until all steps are released and shows the step pressed last, so holding another step switches to its value. Pressing a pad of that row sets the value for all held steps:
  - Velocity (red): 16 pads, 8 per pad, pad 16 = 127
  - Chance (orange): 16 pads, pad 4 = 25%, pad 8 = 50%, pad 16 = 100%
  - Repeats (yellow): as many pads as repeats, the first pad switches them off
  - Recurrence (green): pattern editor as before. The last pad of the row switches the step between Fill on (blue) and Always (grey)
- Modes will reflect step or pad mode and cycle accordingly

### Akai MIDI Mix
- The extension now also drives an Akai MIDI Mix (second MIDI in / out port of the controller) for shaping the sound of 8 pads of the Drum Machine: faders = pad volume, master fader = track volume, bank left / right = next / previous 8 pads
- The three knobs of a channel (top to bottom) have three layouts, remembered per pad: hybrid (default, no LED) = 2nd remote control of the pad's first device, send 1, send 2; parameters (mute button, amber LED) = 1st, 2nd, 3rd remote control; sends (rec arm button, red LED) = send 1, 2, 3. Pressing the lit button goes back to hybrid, solo puts all pads back to hybrid
- The 8 pads of the MIDI Mix always are the left or the right half of the 16 pads on the Fire. Its bank buttons go from half to half and take the Fire's page along; the Fire's Pattern up / down take the MIDI Mix along. Each change flashes the 8 pads on the Fire white three times
- Whatever is moved on the MIDI Mix is shown on the Fire's display: pad, parameter, value and a bar; the knob layout after a switch; and the key range of the 8 pads after a bank change
- The stand-alone MIDI Mix extension has to be switched off in Bitwig when this one is used

### Akai LPD8 mk2
- The extension also listens to an Akai LPD8 mk2 (third MIDI input / output of the controller)
- Pads in note mode: the notes go to Bitwig as they are
- Pads in CC mode (CC 71 - 78): they play the 8 drum pads the MIDI Mix is on, with the value the pad sends as velocity. With the drum track armed the hits arrive as played notes and can be recorded; with it not armed they are played on the track directly, so they are heard anyway (but not recorded)
- Knobs: mapped by hand in Bitwig
- The stand-alone LPD8 extension has to be switched off in Bitwig when this one is used

### Select mode
- Select + Pad: select the relevant instrument device in the currently selected drum chain for the given pad

### Solo mode
- Activate layer by dedicated `Solo` button (instead of retrigger button) 
- Layer is active while the button is pressed. Pin the layer by pressing Shift + Solo.
- Keep clip colors in solo mode

### Mute mode
- Activate layer by dedicated `Mute` button. (instead of resolution button) 
- Layer is active while the button is pressed. Pin the layer by pressing Shift + Mute.
- Keep clip colors in mute mode

### Mute / solo reset
- In the mute and solo layers the LED next to the Select button is green if no pad is muted (soloed) and red if at least one is
- In those layers the Select button unmutes (unsolos) all pads

### Copy mode
- Copy mode: copy the first selected step to the clipboard
- Paste mode: while keeping the copy button pressed paste the copied step to the selected current step

### Select knob
- Select knob affects the currently selected drum rack device as follows: 
  - if 1st in chain is a Sampler: change in steps the 5th param (Pitch)
  - if 1st in chain is a Drum Device (e.g. V9 Kick): change in steps the 1st param (Tune) 
  - if 1st in chain is Note Transpose: change in steps the 2nd param (Semis)
- Push Select knob: reset that parameter (Tune / Pitch) to its default; for a Note Transpose, back to the original pitch of the sample (pad key moved to C3)
- Shift + Select knob: change grid resolution
- Note Repeat enabled + Select knob: change note repeat rate value
