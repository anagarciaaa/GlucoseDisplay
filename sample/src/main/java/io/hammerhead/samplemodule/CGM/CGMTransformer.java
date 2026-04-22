package io.hammerhead.samplemodule.CGM;

import android.util.Log;
import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import io.hammerhead.sample.cgm.CGMStore;
import io.hammerhead.sample.cgm.GlucosePacket;
import io.hammerhead.sdk.v0.SdkContext;
import io.hammerhead.sdk.v0.datatype.Dependency;
import io.hammerhead.sdk.v0.datatype.transformer.SdkTransformer;
import io.hammerhead.samplemodule.ConnexxDataType;

public class CGMTransformer extends SdkTransformer {

    /**
     * Trend is encoded as the fractional part of the returned double so the formatter
     * reads it atomically with the glucose value — no static variable needed.
     *
     * Encoding:  glucose + frac
     *   0.0 = no/unknown direction
     *   0.1 = Flat       →
     *   0.2 = Up         ↑
     *   0.3 = DoubleUp   ↑↑
     *   0.4 = Down       ↓
     *   0.5 = DoubleDown ↓↓
     */

    private double lastGlucose = ConnexxDataType.INVALID_VALUE;
    private double lastGlucoseRate = Double.NaN;
    private long lastFetchTime = 0;
    private static final long FETCH_INTERVAL_MS = 60_000L;

    // Fallback test values when no real CGM data is available
    private final int[] testValues = {70, 90, 110, 145, 200, 280};
    private final String[] testTrends = {"SingleDown", "FortyFiveUp", "Flat", "SingleUp", "DoubleUp", "FortyFiveDown"};
    private final int[] delaysSeconds = {3, 5, 10};
    private int currentIndex = 0;
    private long lastTestUpdateTime = 0;
    private int currentDelay = 3;

    public CGMTransformer(@NonNull SdkContext context) {
        super(context);
    }

    @Override
    public double onDependencyChange(long timeStamp, @NonNull Map<Dependency, Double> dependencies) {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime >= FETCH_INTERVAL_MS) {
            fetchAndStore();
            lastFetchTime = now;
        }

        // Real CGM data from CGMStore (BLE or xDrip)
        GlucosePacket packet = CGMStore.get().getLatest();
        if (packet != null && packet.isUsable() && !packet.isStale()) {
            return encode(packet.glucoseMgDl, packet.trend);
        }

        // Last successfully fetched xDrip value
        if (lastGlucose > 0) {
            return encode((int) lastGlucose, lastGlucoseRate);
        }

        // Demo/test cycling values
        if (lastTestUpdateTime == 0) lastTestUpdateTime = now;
        if ((now - lastTestUpdateTime) >= currentDelay * 1000L) {
            currentIndex = (currentIndex + 1) % testValues.length;
            currentDelay = delaysSeconds[(int) (Math.random() * delaysSeconds.length)];
            lastTestUpdateTime = now;
        }
        return encode(testValues[currentIndex], testTrends[currentIndex]);
    }

    /** Encode glucose + numeric rate (mg/dL/min) as fractional trend code. */
    private double encode(int glucose, double rate) {
        double frac;
        if (Double.isNaN(rate))   frac = 0.0;
        else if (rate >  3.0)     frac = 0.3; // ↑↑
        else if (rate >  1.0)     frac = 0.2; // ↑
        else if (rate > -1.0)     frac = 0.1; // →
        else if (rate > -3.0)     frac = 0.4; // ↓
        else                      frac = 0.5; // ↓↓
        return glucose + frac;
    }

    /** Encode glucose + xDrip direction string as fractional trend code. */
    private double encode(int glucose, String direction) {
        return encode(glucose, trendFromDirection(direction));
    }

    private void fetchAndStore() {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:17580/sgv.json?count=1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    Log.w("CGMTransformer", "xDrip fetch failed: HTTP " + responseCode);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();

                org.json.JSONArray array = new org.json.JSONArray(sb.toString());
                if (array.length() == 0) return;

                org.json.JSONObject latest = array.getJSONObject(0);
                int sgv = latest.getInt("sgv");
                long date = latest.getLong("date");
                String direction = latest.optString("direction", "");
                double rate = direction.isEmpty() ? Double.NaN : trendFromDirection(direction);

                if (sgv <= 13 || sgv >= 401) { Log.w("CGMTransformer", "sgv out of range: " + sgv); return; }
                if ((System.currentTimeMillis() - date) > 10 * 60 * 1000L) { Log.w("CGMTransformer", "reading stale"); return; }

                GlucosePacket packet = GlucosePacket.fromXDrip(sgv, rate, date);
                CGMStore.get().update(packet);

                lastGlucose = sgv;
                lastGlucoseRate = rate;
                Log.d("CGMTransformer", "xDrip fetch OK: sgv=" + sgv + " dir=" + direction);

            } catch (Exception e) {
                Log.e("CGMTransformer", "xDrip fetch exception: " + e);
            }
        }).start();
    }

    private double trendFromDirection(String direction) {
        if (direction == null) return Double.NaN;
        switch (direction) {
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
}
