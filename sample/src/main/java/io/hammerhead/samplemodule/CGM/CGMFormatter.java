package io.hammerhead.samplemodule.CGM;

import androidx.annotation.NonNull;

import io.hammerhead.samplemodule.ConnexxDataType;
import io.hammerhead.sdk.v0.datatype.formatter.SdkFormatter;

public class CGMFormatter extends SdkFormatter {

    /**
     * Decodes the fractional trend code that CGMTransformer encodes into the value:
     *   0.0 = no/unknown direction → no arrow
     *   0.1 = Flat       →
     *   0.2 = Up         ↑
     *   0.3 = DoubleUp   ↑↑
     *   0.4 = Down       ↓
     *   0.5 = DoubleDown ↓↓
     */
    @NonNull
    @Override
    public String formatValue(double value) {
        if (value == ConnexxDataType.INVALID_VALUE || value <= 0) {
            return "---";
        }
        int glucose = (int) value;
        double frac = value - glucose;
        return glucose + arrowFromCode(frac);
    }

    private String arrowFromCode(double frac) {
        switch ((int) Math.round(frac * 10)) {
            case 1:  return "\u2192";           // → Flat
            case 2:  return "\u2191";           // ↑
            case 3:  return "\u2191\u2191";     // ↑↑
            case 4:  return "\u2193";           // ↓
            case 5:  return "\u2193\u2193";     // ↓↓
            default: return "";                 // unknown
        }
    }
}
