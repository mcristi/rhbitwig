## Akai Fire differences from upstream

### Pads
- Shift + Pad ⇒ cycle and apply color from a predefined color list 
- Shift + Clip ⇒ cycle and apply color from a predefined color list
- Shift + Last step => duplicate clip content
- New clip action (on pressing empty pad) copies the previous clip to preserve automations
- Delete clip is the default behavior (clear steps delete the automations as well) 

### Buttons
- Accent value is reflected on the note repeat
- Metronome is mapped to metronome button
- Pin track instead of pin clip
- Start: continue play instead of start
- Shift + Select mode: toggle normal / alternative mode

### Encoders
- New mode: User 2 - first 4 macros from the first device in chain
- New alternate mode: User 2 - last 4 macros from the first device in chain
- Device param names are reflected on the screen
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
- Shift + Select knob: change grid resolution
- Note Repeat enabled + Select knob: change note repeat rate value
