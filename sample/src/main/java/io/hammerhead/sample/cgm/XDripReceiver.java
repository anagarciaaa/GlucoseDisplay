package io.hammerhead.sample.cgm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import timber.log.Timber;

public class XDripReceiver extends BroadcastReceiver {

    public static final String ACTION_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate";
    public static final String ACTION_BG_READINGS = "com.eveningoutpost.dexdrip.BgReadings";
    public static final String ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.NewBgEstimate";

    private static final String EXTRA_BG        = "com.eveningoutpost.dexdrip.Extras.BG_ESTIMATE";
    private static final String EXTRA_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BG_SLOPE_NAME";
    private static final String EXTRA_TIMESTAMP  = "com.eveningoutpost.dexdrip.Extras.TIMESTAMP";
    private static final String EXTRA_READINGS   = "com.eveningoutpost.dexdrip.Extras.BG_READINGS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        Timber.i("XDripReceiver got action: %s", intent.getAction());

        switch (intent.getAction()) {
            case ACTION_BG_ESTIMATE:
            case ACTION_NEW_BG_ESTIMATE:
                handleBgEstimate(intent);
                break;
            case ACTION_BG_READINGS:
                handleBgReadings(intent);
                break;
            default:
                // Try to extract BG from any xDrip intent that has the extras
                tryExtractFromExtras(intent);
                break;
        }
    }

    private void handleBgEstimate(Intent intent) {
        double bgMgDl    = intent.getDoubleExtra(EXTRA_BG, -1);
        String slopeName = intent.getStringExtra(EXTRA_SLOPE_NAME);
        long   timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        if (bgMgDl <= 0) return;
        double trend = trendFromSlopeName(slopeName);
        GlucosePacket packet = GlucosePacket.fromXDrip((int) bgMgDl, trend, timestamp);
        CGMStore.get().update(packet);
        Timber.i("XDripReceiver BgEstimate: %.0f mg/dL %s", bgMgDl, slopeName);
    }

    private void handleBgReadings(Intent intent) {
        // Try extras-based BG first
        double bgMgDl = intent.getDoubleExtra(EXTRA_BG, -1);
        if (bgMgDl > 13 && bgMgDl < 401) {
            handleBgEstimate(intent);
            return;
        }

        // Try JSON array
        String json = intent.getStringExtra(EXTRA_READINGS);
        if (json == null) json = intent.getStringExtra("data");
        if (json == null) return;
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) return;
            JSONObject latest = array.getJSONObject(0);
            double bg        = latest.optDouble("sgv", latest.optDouble("calculated_value", -1));
            long   timestamp = latest.optLong("date", latest.optLong("timestamp", System.currentTimeMillis()));
            String direction = latest.optString("direction", latest.optString("slope_name", ""));
            if (bg <= 0 || bg > 401) return;
            double trend = trendFromDirection(direction);
            GlucosePacket packet = GlucosePacket.fromXDrip((int) bg, trend, timestamp);
            CGMStore.get().update(packet);
            Timber.i("XDripReceiver BgReadings JSON: %.0f mg/dL %s", bg, direction);
        } catch (Exception e) {
            Timber.w("XDripReceiver: JSON parse failed: %s", e.getMessage());
        }
    }

    private void tryExtractFromExtras(Intent intent) {
        double bgMgDl = intent.getDoubleExtra(EXTRA_BG, -1);
        if (bgMgDl > 13 && bgMgDl < 401) {
            handleBgEstimate(intent);
        }
    }

    private double trendFromSlopeName(String name) {
        if (name == null) return Double.NaN;
        switch (name) {
            case "DoubleUp":      return  4.0;
            case "SingleUp":      return  2.5;
            case "FortyFiveUp":   return  1.5;
            case "Flat":          return  0.0;
            case "FortyFiveDown": return -1.5;
            case "SingleDown":    return -2.5;
            case "DoubleDown":    return -4.0;
            default:              return Double.NaN;
        }
    }

    private double trendFromDirection(String direction) {
        return trendFromSlopeName(direction);
    }
}