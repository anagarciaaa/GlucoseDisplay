package io.hammerhead.sample.cgm;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Set;

import timber.log.Timber;

/**
 * Listens for {@link BluetoothDevice#ACTION_ACL_CONNECTED} and connects to
 * the Stelo via GATT when it is one of our bonded Dexcom devices.
 *
 * The Stelo auto-reconnects over BLE every ~5 minutes once bonded.
 * This receiver catches that event and initiates a GATT connection without
 * needing an active BLE scan — saving battery and simplifying state management.
 *
 * Registration:
 *   Registered/unregistered dynamically by {@link CGMService} so it is only
 *   active while the foreground service is running.
 *
 * First-time pairing:
 *   The device must already be bonded via {@link SteloScanner#startPairingScan}.
 *   After bonding, this receiver takes over permanently.
 */
@SuppressLint("MissingPermission")
public class SteloReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;

        BluetoothDevice connectedDevice =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (connectedDevice == null) return;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        // Walk the bonded devices list and check if the newly connected device
        // is a bonded Dexcom sensor (name starts with "DXC" or "Dex").
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        for (BluetoothDevice device : bonded) {
            if (!device.getAddress().equals(connectedDevice.getAddress())) continue;

            String name = device.getName();
            if (!DexcomSteloProtocol.isDexcomName(name)) continue;

            Timber.i("CGM: Dexcom device ACL_CONNECTED — %s (%s), initiating GATT",
                    name, device.getAddress());

            // autoConnect=false: faster initial connection; we rely on ACL events for reconnect.
            device.connectGatt(context.getApplicationContext(), false, new SteloGattCallback());
            return; // only connect to the first matching bonded device
        }
    }
}
