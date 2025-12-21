package com.akai.fire;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.DrumPad;

public class ColorUtils {
    private static final Color[] colors = {
        Color.fromHex("#d92e22"), // red
        Color.fromHex("#ff5704"), // orange
        Color.fromHex("#d99d0e"), // yellow
        Color.fromHex("#0066aa"), // dark blue
        Color.fromHex("#88eeff"), // light blue
        Color.fromHex("#2a8844"), // dark green
        Color.fromHex("#44ff22"), // light green
        Color.fromHex("#9549cb"), // dark purple
        Color.fromHex("#d1b9db"), // light purple
    };

    public static Color getColor(ClipLauncherSlot slot) {
        return getNextColor(slot.color().get());
    }

    public static Color getColor(DrumPad pad) {
        return getNextColor(pad.color().get());
    }

    private static Color getNextColor(Color currentColor) {
        int colorIndex = 0;
        for (int i = 0; i < colors.length; i++) {
            if (
                // NOTE: this is a workaround for V6 color issues
                Math.abs(colors[i].getRed255() - currentColor.getRed255()) < 10 &&
                Math.abs(colors[i].getGreen255() - currentColor.getGreen255()) < 10 &&
                Math.abs(colors[i].getBlue255() - currentColor.getBlue255()) < 10
            ) {
                colorIndex = i + 1;
            }
        }
        if (colorIndex == colors.length) {
            colorIndex = 0;
        }
        return colors[colorIndex];
    }
}
