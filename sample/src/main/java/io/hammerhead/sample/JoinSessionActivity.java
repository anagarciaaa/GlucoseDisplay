package io.hammerhead.sample;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import timber.log.Timber;

public class JoinSessionActivity extends BaseActivity {

    private final ArrayList<BluetoothDeviceWrapper> devices = new ArrayList<>();
    private ArrayAdapter<BluetoothDeviceWrapper> deviceViewAdapter;
    private final String LOG_TAG = "JoinSessionActivity";

    //Broadcast receiver to receive intents triggered by Bluetooth discovery
    public final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Timber.tag(LOG_TAG).i("received action: %s", action);
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                if(deviceName != null){
                    Timber.tag(LOG_TAG).i("found device: %s", deviceName);
                    devices.add(new BluetoothDeviceWrapper(device));
                    deviceViewAdapter.notifyDataSetChanged();
                }
            }
        }
    };


    //Helper class to change toString of BluetoothDevice
    static private class BluetoothDeviceWrapper {
        public BluetoothDevice device;
        public BluetoothDeviceWrapper(BluetoothDevice d) {
            this.device = d;
        }

        @NonNull
        @Override
        public String toString() {
            return this.device.getName();
        }
    }

    //Displays list of devices and sets onClickListener to return the selected device to the calling activity
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        deviceViewAdapter = new ArrayAdapter<BluetoothDeviceWrapper>(
                this,
                R.layout.activity_device_item,
                R.id.device_item_text,
                this.devices
        );
        initBaseLayout(R.layout.activity_joinsession);

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothReceiver, filter);

        ListView deviceListView = findViewById(R.id.devices_list);
        deviceListView.setAdapter(this.deviceViewAdapter);
        deviceListView.setOnItemClickListener((adapterView, view, i, l) -> {
            Intent activityResult = new Intent();
            BluetoothDevice device = devices.get(i).device;
            activityResult.putExtra("device", device);
            setResult(RESULT_OK, activityResult);
            finish();
        });

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

        //check and request location permission (no ACTION_FOUND intents will be received with explicit location permission)
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permissionCheck != 0) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
        //if bluetooth is unavailable, return an error
        if(btAdapter == null){
            Timber.tag(LOG_TAG).i("Bluetooth adapter is null");
            Intent activityResult = new Intent();
            activityResult.putExtra("error", "Bluetooth adapter is null");
            setResult(RESULT_CANCELED, activityResult);
            finish();
            return;
        }
        if (!btAdapter.isEnabled()){
            Timber.tag(LOG_TAG).i("Bluetooth not enabled");
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, 1);
        }
        else {
            Timber.tag(LOG_TAG).i("Bluetooth is available");
        }
        startDiscovery(btAdapter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 1){
            if(resultCode == RESULT_OK){
                Timber.tag(LOG_TAG).i("Bluetooth enabled");
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                startDiscovery(btAdapter);
            }
            else{
                Timber.tag(LOG_TAG).i("Bluetooth not enabled by user");
                Intent activityResult = new Intent();
                activityResult.putExtra("error", "Bluetooth not enabled by user");
                setResult(RESULT_CANCELED, activityResult);
                finish();
            }
        }
    }

    private void startDiscovery(BluetoothAdapter btAdapter){
        if(btAdapter.isDiscovering()){
            btAdapter.cancelDiscovery();
        }
        btAdapter.startDiscovery();
        Timber.tag(LOG_TAG).i("started discovery");
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(bluetoothReceiver);
        super.onDestroy();
    }
}