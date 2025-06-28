package com.akai.fire.sequence;

import com.akai.fire.lights.BiColorLightState;

public enum EncoderMode {
    CHANNEL(BiColorLightState.MODE_CHANNEL, "1: Velocity\n2: Chance\n3: Repeats\n4: Occurrence", //
            new EncoderAccess[]{NoteStepAccess.VELOCITY, NoteStepAccess.CHANCE, NoteStepAccess.REPEATS, NoteStepAccess.RECURRENCE}),
    MIXER(BiColorLightState.MODE_MIXER, "1: Velocity Spread\n2: Pressure\n3: Length\n4: Timbre",
            new EncoderAccess[]{NoteStepAccess.VELOCITY_SPREAD, NoteStepAccess.PRESSURE, //
                    NoteStepAccess.DURATION, NoteStepAccess.TIMBRE}), //
    MIXER_SHIFT(BiColorLightState.MODE_MIXER, "1: Repeat Curve\n2: Repeat Vel Curve\n3: Repeat Vel End\n4: Occurrence",
            new EncoderAccess[]{NoteStepAccess.REPEATCURVE, NoteStepAccess.REPEAT_VEL_CRV, //
                    NoteStepAccess.REPEAT_VEL_END, NoteStepAccess.OCCURENCE}),
    USER_1(BiColorLightState.MODE_USER1, "1: Level\n2: Pan\n3: Fx1\n4: Fx2", new EncoderAccess[]{}),
	USER_2(BiColorLightState.MODE_USER2, "1: Attack / Macro 1\n2: Decay / Macro 2\n3: Sustain / Macro 3\n4: Release / Macro 4", new EncoderAccess[]{}),
	USER_2_SHIFT(BiColorLightState.MODE_USER2, "1: Macro 5\n2: Macro 6\n3: Macro 7\n4: Macro 8", new EncoderAccess[]{});

    private final BiColorLightState state;
    private final String info;
    private final EncoderAccess[] assignments;

    EncoderMode(final BiColorLightState state, final String info, final EncoderAccess[] assignments) {
        this.state = state;
        this.info = info;
        this.assignments = assignments;
    }

    public EncoderAccess[] getAssignments() {
        return assignments;
    }

    public BiColorLightState getState() {
        return state;
    }

    public String getInfo() {
        return info;
    }
}