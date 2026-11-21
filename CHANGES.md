## Akai Fire differences from upstream

### Pads
- Shift + Pad ⇒ cycle and apply color from a predefined color list 
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
  - Recurrence (green): pattern editor as before
- Modes will reflect step or pad mode and cycle accordingly

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
