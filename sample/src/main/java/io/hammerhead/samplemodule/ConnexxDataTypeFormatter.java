package io.hammerhead.samplemodule;

import androidx.annotation.NonNull;

import io.hammerhead.sdk.v0.datatype.formatter.SdkFormatter;

public class ConnexxDataTypeFormatter extends SdkFormatter {
    public int precision;
    public boolean usePercentage;
    ConnexxDataTypeFormatter(int precision, boolean usePercentage) {
        super();
        this.precision = precision;
        this.usePercentage = usePercentage;
    }

    @NonNull
    @Override
    public String formatValue(double value) {
        if(value == ConnexxDataType.INVALID_VALUE){
            return "N/A";
        }
        if(usePercentage){
            return String.format("%.0f", value);
        }
        return String.format("%." + precision + "f", value);
    }
}