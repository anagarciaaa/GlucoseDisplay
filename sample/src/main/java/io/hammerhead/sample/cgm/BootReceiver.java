package io.hammerhead.sample.cgm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import timber.log.Timber;

/**
 * Starts CGMService automatically on device boot so the Karoo
 * data field has glucose data without needing to open the app.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Timber.i("BootReceiver: starting CGMService after boot");
        Intent serviceIntent = new Intent(context, CGMService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
