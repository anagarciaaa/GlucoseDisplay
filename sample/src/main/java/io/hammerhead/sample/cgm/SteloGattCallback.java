package io.hammerhead.sample.cgm;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

import java.util.UUID;

import timber.log.Timber;

/**
 * GATT callback for the Dexcom Stelo / G7 BLE connection.
 *
 * Connection flow:
 *   STATE_CONNECTED → discoverServices()
 *   onServicesDiscovered → enable notifications on Control, then ProbablyBackfill
 *   onCharacteristicChanged → parse glucose packet → update CGMStore
 *
 * GATT operations (writeDescriptor) must be sequential on Android —
 * we chain the second enableNotifications call inside onDescriptorWrite
 * after the first CCCD write completes.
 */
public class SteloGattCallback extends BluetoothGattCallback {

    // Track which CCCD write we're waiting on so we can chain the second one.
    private boolean controlNotificationsEnabled = false;

    // -------------------------------------------------------------------------
    // Connection state
    // -------------------------------------------------------------------------

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Timber.i("CGM: GATT connected, discovering services");
            controlNotificationsEnabled = false;
            gatt.discoverServices();

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Timber.i("CGM: GATT disconnected (status=%d) — sensor will reconnect in ~5 min", status);
            gatt.close();
            // SteloReceiver will fire again on the next ACL_CONNECTED broadcast.
        }
    }

    // -------------------------------------------------------------------------
    // Service discovery
    // -------------------------------------------------------------------------

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Timber.e("CGM: Service discovery failed (status=%d)", status);
            return;
        }

        BluetoothGattService service = gatt.getService(DexcomSteloProtocol.CGM_SERVICE_UUID);
        if (service == null) {
            Timber.e("CGM: CGM service (F8083532…) not found — verify CGM_SERVICE_UUID");
            return;
        }

        // Enable Control notifications first; Backfill is chained in onDescriptorWrite.
        enableNotifications(gatt, service, DexcomSteloProtocol.CONTROL_UUID);
    }

    // -------------------------------------------------------------------------
    // Descriptor writes — chain Control → Backfill sequentially
    // -------------------------------------------------------------------------

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                  int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Timber.w("CGM: Descriptor write failed (status=%d, uuid=%s)",
                    status, descriptor.getUuid());
        }

        // After the Control CCCD write completes, enable Backfill notifications.
        if (!controlNotificationsEnabled) {
            controlNotificationsEnabled = true;
            BluetoothGattService service = gatt.getService(DexcomSteloProtocol.CGM_SERVICE_UUID);
            if (service != null) {
                enableNotifications(gatt, service, DexcomSteloProtocol.BACKFILL_UUID);
                Timber.i("CGM: Both notification channels enabled");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Glucose notifications
    // -------------------------------------------------------------------------

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        byte[] data = characteristic.getValue();

        if (DexcomSteloProtocol.CONTROL_UUID.equals(uuid)) {
            GlucosePacket packet = GlucosePacket.parse(data);
            if (packet == null) return;

            if (packet.isUsable()) {
                Timber.i("CGM: glucose=%d mg/dL  trend=%s  predicted=%d  displayOnly=%b",
                        packet.glucoseMgDl, packet.trendSymbol(),
                        packet.predicted, packet.displayOnly);
                CGMStore.get().update(packet);
            } else {
                Timber.d("CGM: Packet received but not usable "
                        + "(glucose=%d, displayOnly=%b, calibState=%d)",
                        packet.glucoseMgDl, packet.displayOnly, packet.calibState);
            }

        } else if (DexcomSteloProtocol.BACKFILL_UUID.equals(uuid)) {
            // Backfill (historical) packets — parsing can be added here if needed.
            Timber.d("CGM: Backfill packet received (%d bytes)", data != null ? data.length : 0);
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void enableNotifications(BluetoothGatt gatt, BluetoothGattService service,
                                     UUID charUuid) {
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
        if (characteristic == null) {
            Timber.w("CGM: Characteristic %s not found in service", charUuid);
            return;
        }

        // setCharacteristicNotification is a local-only call (no BLE traffic).
        gatt.setCharacteristicNotification(characteristic, true);

        // Writing the CCCD descriptor sends the actual BLE enable-notification command.
        BluetoothGattDescriptor cccd = characteristic.getDescriptor(DexcomSteloProtocol.CCCD_UUID);
        if (cccd != null) {
            cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(cccd);
        } else {
            Timber.w("CGM: CCCD descriptor not found on characteristic %s", charUuid);
        }
    }
}
