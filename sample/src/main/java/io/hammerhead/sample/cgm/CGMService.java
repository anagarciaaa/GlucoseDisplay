package io.hammerhead.sample.cgm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import io.hammerhead.sample.R;
import timber.log.Timber;

/**
 * Foreground service that polls xDrip's web service at localhost:17580
 * every 30 seconds and writes readings into CGMStore.
 *
 * Also registers XDripReceiver for broadcast fallback.
 */
public class CGMService extends Service {

    private static final String NOTIFICATION_CHANNEL_ID = "cgm_channel";
    private static final int    NOTIFICATION_ID          = 1002;
    private static final long   POLL_INTERVAL_MS         = 30_000L;

    private XDripReceiver xDripReceiver;
    private Handler pollHandler;
    private Runnable pollRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        pollHandler = new Handler(Looper.getMainLooper());
        Timber.i("CGMService: created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Monitoring xDrip..."));
        } catch (SecurityException e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Register broadcast receiver as fallback
        if (xDripReceiver == null) {
            xDripReceiver = new XDripReceiver();
            IntentFilter filter = new IntentFilter(XDripReceiver.ACTION_BG_ESTIMATE);
            filter.addAction(XDripReceiver.ACTION_BG_READINGS);
            filter.addAction(XDripReceiver.ACTION_NEW_BG_ESTIMATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(xDripReceiver, filter, RECEIVER_EXPORTED);
            } else {
                registerReceiver(xDripReceiver, filter);
            }
        }

        // Start polling web service
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                pollXDripWebService();
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        pollHandler.post(pollRunnable);

        return START_STICKY;
    }

    private void pollXDripWebService() {
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:17580/sgv.json?count=1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) return;

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
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
                double trend = direction.isEmpty() ? Double.NaN : trendFromDirection(direction);

                if (sgv > 13 && sgv < 401) {
                    GlucosePacket packet = GlucosePacket.fromXDrip(sgv, trend, date);
                    CGMStore.get().update(packet);
                    Timber.i("CGMService poll: %d mg/dL %s", sgv, direction);
                }
            } catch (Exception e) {
                Timber.w("CGMService poll failed: %s", e.getMessage());
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

    @Override
    public void onDestroy() {
        pollHandler.removeCallbacks(pollRunnable);
        if (xDripReceiver != null) {
            unregisterReceiver(xDripReceiver);
            xDripReceiver = null;
        }
        CGMStore.get().clear();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "CGM Glucose Monitor",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String status) {
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Glucose Monitor")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic)
                .setOngoing(true)
                .build();
    }
}