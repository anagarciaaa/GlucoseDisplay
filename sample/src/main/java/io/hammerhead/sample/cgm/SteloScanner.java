package io.hammerhead.sample.cgm;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.Collections;

import timber.log.Timber;

/**
 * One-time BLE scanner for first-time pairing with the Dexcom Stelo.
 *
 * After the user taps "Pair Sensor" in the UI:
 *   1. Call {@link #startPairingScan(PairingCallback)}.
 *   2. Scans for devices advertising UUID 0000FEBC… (Stelo advertisement UUID).
 *   3. On find: calls {@link BluetoothDevice#createBond()} — system pairing dialog appears.
 *   4. After bonding, {@link SteloReceiver} handles all future connections automatically.
 *
 * Requires ACCESS_FINE_LOCATION at runtime and Location Services enabled —
 * BLE scanning silently returns nothing on API 26–30 without both.
 *
 * @SuppressLint("MissingPermission") is used throughout because permissions are declared
 * in the manifest and granted at runtime before this class is used. Lint cannot verify
 * that statically, so we suppress the false-positive warnings.
 */
@SuppressLint("MissingPermission")
public class SteloScanner {

    private static final long SCAN_TIMEOUT_MS = 30_000;

    public interface PairingCallback {
        void onDeviceFound(BluetoothDevice device);
        void onScanFailed(int errorCode);
        void onTimeout();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private ScanCallback activeScanCallback;
    private boolean scanning = false;

    public SteloScanner(Context context) {}

    public void startPairingScan(final PairingCallback callback) {
        if (scanning) {
            Timber.w("CGM: Scan already in progress");
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Timber.e("CGM: Bluetooth not available");
            callback.onScanFailed(-1);
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            Timber.e("CGM: BluetoothLeScanner unavailable");
            callback.onScanFailed(-1);
            return;
        }

        ScanFilter serviceFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(DexcomSteloProtocol.ADVERTISEMENT_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        activeScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String name = device.getName();
                if (!DexcomSteloProtocol.isDexcomName(name)) return;

                Timber.i("CGM: Found Stelo — %s (%s), initiating bond", name, device.getAddress());
                stopScan();
                device.createBond();
                callback.onDeviceFound(device);
            }

            @Override
            public void onScanFailed(int errorCode) {
                Timber.e("CGM: Scan failed (errorCode=%d). "
                        + "Ensure ACCESS_FINE_LOCATION is granted and Location Services are on.",
                        errorCode);
                scanning = false;
                activeScanCallback = null;
                callback.onScanFailed(errorCode);
            }
        };

        Timber.i("CGM: Starting pairing scan (timeout=%ds)", SCAN_TIMEOUT_MS / 1000);
        scanning = true;
        scanner.startScan(Collections.singletonList(serviceFilter), settings, activeScanCallback);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanning) {
                    Timber.w("CGM: Pairing scan timed out");
                    stopScan();
                    callback.onTimeout();
                }
            }
        }, SCAN_TIMEOUT_MS);
    }

    public void stopScan() {
        handler.removeCallbacksAndMessages(null);
        if (scanning && scanner != null && activeScanCallback != null) {
            try {
                scanner.stopScan(activeScanCallback);
            } catch (Exception ignored) {}
        }
        scanning = false;
        activeScanCallback = null;
    }
}
