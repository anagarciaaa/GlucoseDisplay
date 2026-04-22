package io.hammerhead.sample;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.hammerhead.sample.cgm.CGMService;
import timber.log.Timber;

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.Tree() {
            @Override
            protected void log(int priority, @Nullable String tag, @NonNull String message, @Nullable Throwable t) {
                Log.println(priority, tag, t != null ? t.getMessage() : message);
            }
        });

        // Auto-start CGMService so the Karoo data field works without opening the app
        try {
            Intent serviceIntent = new Intent(this, CGMService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Timber.w("SampleApplication: could not start CGMService: %s", e.getMessage());
        }
    }
}
