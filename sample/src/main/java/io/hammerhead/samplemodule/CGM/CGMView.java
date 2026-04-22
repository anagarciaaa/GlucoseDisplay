package io.hammerhead.samplemodule.CGM;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import io.hammerhead.samplemodule.ConnexxDataType;
import io.hammerhead.sdk.v0.SdkContext;
import io.hammerhead.sdk.v0.datatype.view.SdkView;

public class CGMView extends SdkView {

    private static final String TAG_GLUCOSE = "glucose_value";

    public CGMView(@NotNull SdkContext context) {
        super(context);
    }

    @Override
    protected View createView(@NotNull LayoutInflater layoutInflater, @NotNull ViewGroup parent) {
        LinearLayout container = new LinearLayout(layoutInflater.getContext());
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        container.setGravity(Gravity.CENTER);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.TRANSPARENT);

        TextView tv = new TextView(layoutInflater.getContext());
        tv.setTag(TAG_GLUCOSE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Color.parseColor("#AAAAAA"));

        container.addView(tv);
        return container;
    }

    @Override
    public void onUpdate(@NotNull View view, double value, @Nullable String formattedValue) {
        TextView tv = (TextView) view.findViewWithTag(TAG_GLUCOSE);
        if (tv == null) return;

        if (value == ConnexxDataType.INVALID_VALUE || value <= 0) {
            tv.setText("---");
            tv.setTextColor(Color.GRAY);
            return;
        }

        int glucose = (int) value;
        double frac = value - glucose;
        tv.setText(glucose + " " + directionToArrow(frac));
        tv.setTextColor(colorForGlucose(glucose));
    }

    @Override
    public void onInvalid(@NotNull View view) {
        TextView tv = (TextView) view.findViewWithTag(TAG_GLUCOSE);
        if (tv == null) return;
        tv.setText("---");
        tv.setTextColor(Color.GRAY);
    }

    private String directionToArrow(double frac) {
        switch ((int) Math.round(frac * 10)) {
            case 1:  return "\u2192";       // → Flat
            case 2:  return "\u2191";       // ↑
            case 3:  return "\u2191\u2191"; // ↑↑
            case 4:  return "\u2193";       // ↓
            case 5:  return "\u2193\u2193"; // ↓↓
            default: return "";
        }
    }

    private int colorForGlucose(int glucose) {
        if (glucose < 70)        return Color.parseColor("#FF3333");
        else if (glucose < 80)   return Color.parseColor("#FF8800");
        else if (glucose <= 180) return Color.parseColor("#00CC44");
        else if (glucose <= 250) return Color.parseColor("#FF8800");
        else                     return Color.parseColor("#FF3333");
    }
}
