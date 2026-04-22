package io.hammerhead.sample.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.EnumMap;
import java.util.UUID;

import io.hammerhead.sample.KeyConstants;
import io.hammerhead.sample.R;
import io.hammerhead.sample.service.ConnectionHandler.ConnectionState;
import io.hammerhead.samplemodule.ConnexxDataType;
import timber.log.Timber;

//Foreground service that handles the bluetooth connection to another Karoo device
public class PartnerService extends Service {
    private final String notificationChannelId = "bt_channel";
    private static final String LOG_TAG = "PartnerService";
    private boolean connectAsClient;
    private ConnectionHandler connectionHandler;
    private final Handler connectionCallbackHandler = new Handler(Looper.myLooper());
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private Notification foregroundNotification;
    //Message that is saved when max hr and max power are requested but not available,
    //so that the service can send the data when it becomes available
    EnumMap<FitnessMetric, Message> partnerMaxValueRequests = new EnumMap<>(FitnessMetric.class);

    public static final UUID SERVICE_UUID = UUID.fromString("1169eaaf-176c-417f-ac07-39101502c738");
    
    public enum MessageType {
        GET_METRIC,
        SET_METRIC,
        DISCONNECT
    }
    public enum FitnessMetric {
        HEART_RATE,
        SPEED,
        POWER,
        //TODO: refactor into separate enum for max values?
        MAX_HEART_RATE,
        FTP,
        CONNECTION_STATUS;
    }

    public static final String PARTNER_NAME_RECEIVED = "io.hammerhead.sample.service.PARTNER_NAME_RECEIVED";
    public static final String CONNECTION_STATE_CHANGED = "io.hammerhead.sample.service.CONNECTION_STATE_CHANGED";
    public static final String BIND_LOCAL = "io.hammerhead.sample.service.BIND_LOCAL";
    public static final String BIND_REMOTE = "io.hammerhead.sample.service.BIND_REMOTE";

    public class LocalBinder extends Binder {
        public PartnerService getService(){
            return PartnerService.this;
        }
    }
    private final IBinder localBinder = new LocalBinder();

    private final Handler messageHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            int operation = message.what;
            if(operation == MessageType.GET_METRIC.ordinal()){
                handleGetMessage(message);
            }
            else if(operation == MessageType.SET_METRIC.ordinal()){
                handleSetMessage(message);
            }
            else{
                Timber.tag(LOG_TAG).e("Unknown message type: %d", operation);
            }
            return true;
        }

        private void handleGetMessage(Message message){
            if(message.replyTo == null){
                Timber.tag(LOG_TAG).e("No replyTo found in message");
                return;
            }
            int sensorTypeInt = message.arg1;
            if(sensorTypeInt < 0 || sensorTypeInt >= FitnessMetric.values().length){
                Timber.tag(LOG_TAG).e("Invalid sensor type: %d", sensorTypeInt);
                return;
            }
            FitnessMetric fitnessMetric = FitnessMetric.values()[sensorTypeInt];
            Double value = getMetricValue(fitnessMetric);
            if(fitnessMetric == FitnessMetric.FTP || fitnessMetric == FitnessMetric.MAX_HEART_RATE){
                Timber.tag(LOG_TAG).i("Max value requested, sensor type = %s", fitnessMetric.name());
                if(value == null){
                    Timber.tag(LOG_TAG).i("Max value not available, saving message for later");
                    //create a copy of the message so that it can be sent later (messages can only be sent once)
                    Message newMessage = Message.obtain();
                    newMessage.copyFrom(message);
                    partnerMaxValueRequests.put(fitnessMetric, newMessage);
                    return;
                }
            }
            //Timber.tag(LOG_TAG).i("Sending value %f for sensor type %s", value, sensorType.name());
            replyToMessage(message, value);
        }

        private void handleSetMessage(Message message){
            Bundle bundle = message.getData();
            if(bundle == null){
                Timber.tag(LOG_TAG).e("No data found in message");
                return;
            }
            double val = bundle.getDouble(KeyConstants.METRIC_VALUE, ConnexxDataType.INVALID_VALUE);
            int sensorTypeInt = message.arg1;
            if(sensorTypeInt < 0 || sensorTypeInt >= FitnessMetric.values().length){
                Timber.tag(LOG_TAG).e("Invalid sensor type: %d", sensorTypeInt);
                return;
            }
            FitnessMetric fitnessMetric = FitnessMetric.values()[sensorTypeInt];
            if(connectionHandler == null){
                //Timber.tag(LOG_TAG).e("No connectionHandler found");
                return;
            }
            connectionHandler.userData.put(fitnessMetric.name(), val);
        }

        private void replyToMessage(Message message, Double val){
            if(val == null){
                Timber.tag(LOG_TAG).w("Value is null, type = %d, metric = %d", message.what, message.arg1);
                val = -1.0;
            }
            int sensorType = message.arg1;
            Message reply = Message.obtain(null, sensorType);
            Bundle bundle = new Bundle();
            bundle.putDouble(KeyConstants.METRIC_VALUE, val);
            reply.setData(bundle);
            try{
                //Timber.tag(LOG_TAG).i("Sending message of type %d with data %f", message.what, val);
                message.replyTo.send(reply);
            }
            catch (RemoteException e){
                Timber.tag(LOG_TAG).e(e, "Error sending message for type %d", message.what);
            }
        }
    });
    private final Messenger messenger = new Messenger(messageHandler);

    private Double getMetricValue(FitnessMetric metric){
        if(connectionHandler == null){
            //Timber.tag(LOG_TAG).e("getMetricValue called with null connectionHandler");
            return ConnexxDataType.INVALID_VALUE;
        }
        return connectionHandler.partnerData.get(metric.name());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Timber.tag(LOG_TAG).i("onBind called for service");
        String action = intent.getAction();
        if(action == null){
            Timber.tag(LOG_TAG).e("onBind called with null action");
            return null;
        }
        else if(action.equals(BIND_LOCAL)){
            Timber.tag(LOG_TAG).i("onBind returning local binder");
            return localBinder;
        }
        else if(action.equals(BIND_REMOTE)){
            Timber.tag(LOG_TAG).i("onBind returning remote binder");
            return messenger.getBinder();
        }
        else{
            Timber.tag(LOG_TAG).e("onBind called with invalid action %s", action);
        }
        return null;
    }

    public ConnectionState getConnectionState(){
        if(connectionHandler == null){
            return ConnectionState.DISCONNECTED;
        }
        return connectionState;
    }

    public String getPartnerName(){
        return connectionHandler == null ? null : connectionHandler.getPartnerName();
    }

    public void disconnect(){
        Timber.tag(LOG_TAG).i("Disconnecting from partner");
        if(waitingForActivityResult){
            Timber.tag(LOG_TAG).i("Cancelling bluetooth enable request and stopping service");
            waitingForActivityResult = false;
            connectionHandler = null;
            connectionListener.onDisconnected();
        }
        else if(connectionHandler != null){
            connectionHandler.stopHandler();
        }
    }

    @Override
    public void onCreate(){
        super.onCreate();
        Timber.tag(LOG_TAG).i("onCreate called for service");
        //Notification channels are required for Android 8.0 and above
        createNotificationChannel();
        //required by Android for startForeground()
        this.foregroundNotification = new NotificationCompat.Builder(this, this.notificationChannelId)
                .setChannelId(this.notificationChannelId)
                .setContentTitle("ConneXX Bluetooth Connection")
                .setContentText("Partner Connection")
                .setSmallIcon(R.drawable.ic)
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .build();

        registerReceiver(adapterStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        //startForeground(1, this.foregroundNotification);
    }

    private int adapterState = BluetoothAdapter.STATE_ON;
    private boolean waitingForActivityResult = false;
    //TODO: use timeout
    private void startConnectionHandler(){
        if(connectAsClient){
            connectionHandler.startAsClient();
        }
        else{
            connectionHandler.startAsServer();
        }
    }
    BroadcastReceiver adapterStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            Timber.tag(LOG_TAG).i("Bluetooth adapter state changed to %d", newState);
            if(newState == BluetoothAdapter.ERROR){
                Timber.tag(LOG_TAG).e("Error getting adapter state");
                return;
            }
            adapterState = newState;
            if(waitingForActivityResult){
                if(adapterState == BluetoothAdapter.STATE_ON){
                    waitingForActivityResult = false;
                    Timber.tag(LOG_TAG).i("Bluetooth enabled, starting connection handler");
                    connectionHandler.waitForThread();
                    startConnectionHandler();
                }
                else if(adapterState != BluetoothAdapter.STATE_TURNING_ON){
                    //TODO: handle user potentially not allowing bluetooth
                    Timber.tag(LOG_TAG).e("Bluetooth not enabled, stopping service");
                }
            }
        }
    };

    ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onStateChanged(ConnectionState state) {
            connectionCallbackHandler.post(() -> {
                Timber.tag(LOG_TAG).i("Connection state changed to %s, broadcasting", state.name());
                connectionState = state;
                Intent intent = new Intent(CONNECTION_STATE_CHANGED);
                intent.putExtra(KeyConstants.CONNECTION_STATE, state);
                sendBroadcast(intent);
            });
        }

        @Override
        public void onDisconnected() {
            connectionCallbackHandler.post(() -> {
                Timber.tag(LOG_TAG).i("Disconnected from partner, stopping service");
                connectionHandler = null;
                stopForeground(true);
                stopSelf();
            });
        }

        @Override
        public void onPartnerMetricReceived(FitnessMetric metric, double value) {
            connectionCallbackHandler.post(() -> {
                Timber.tag(LOG_TAG).i("%s observer notified with value %f", metric.name(), value);
                Message maxValueRequest = partnerMaxValueRequests.get(metric);
                if(maxValueRequest == null){
                    return;
                }
                partnerMaxValueRequests.remove(metric);
                Timber.tag(LOG_TAG).i("Sending %s value to connexx module", metric.name());
                messageHandler.sendMessage(maxValueRequest);
            });
        }

        @Override
        public void onPartnerNameReceived(String name){
            connectionCallbackHandler.post(() -> {
                Timber.tag(LOG_TAG).i("Received partner name: %s", name);
                Intent intent = new Intent(PARTNER_NAME_RECEIVED);
                intent.putExtra("partnerName", name);
                sendBroadcast(intent);
            });
        }

        @Override
        public void onRetry(int retryCount){
            connectionCallbackHandler.post(() -> {
                Timber.tag(LOG_TAG).i("Retrying connection");
                if(adapterState != BluetoothAdapter.STATE_ON) {
                    waitingForActivityResult = true;
                    startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                    Timber.tag(LOG_TAG).i("Bluetooth not enabled, waiting for user to enable");
                    return;
                }
                if(connectAsClient){
                    Timber.tag(LOG_TAG).i("Retrying as client in %d seconds", retryCount);
                    //Retry less frequently as we fail more, to give the users time to get closer or to an area with less interference
                    connectionCallbackHandler.postDelayed(() -> {
                        Timber.tag(LOG_TAG).i("Retrying as client");
                        connectionHandler.startAsClient();
                    }, retryCount * 1000L);
                }
                else{
                    Timber.tag(LOG_TAG).i("Retrying as server");
                    connectionHandler.startAsServer();
                }
            });
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Timber.tag(LOG_TAG).i("onStartCommand called for service");

        /*If startForegroundService() is called after the service is created,
        Android will kill the app (ANR) if we don't call startForeground() within 5 seconds.
        This method gets called every time startForegroundService() is called.
        */
        startForeground(1, this.foregroundNotification);

        if (!intent.hasExtra(KeyConstants.CONNECT_AS_CLIENT)) {
            Timber.tag(LOG_TAG).e("onStartCommand called with no connectAsClient extra");
            stopForeground(true);
            return START_NOT_STICKY;
        }
        connectAsClient = intent.getBooleanExtra(KeyConstants.CONNECT_AS_CLIENT, false);
        if(connectionHandler != null){
            Timber.tag(LOG_TAG).e("onStartCommand called with active connection. Ignoring");
            return START_NOT_STICKY;
        }
        String userName = intent.getStringExtra(KeyConstants.USER_NAME);
        double maxHR = intent.getIntExtra(KeyConstants.USER_MAX_HR, (int)ConnexxDataType.INVALID_VALUE);
        double ftp = intent.getIntExtra(KeyConstants.USER_FTP, (int)ConnexxDataType.INVALID_VALUE);


        //TODO: check if device is null and if bluetooth is enabled
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = intent.getParcelableExtra(KeyConstants.DEVICE);
        connectionHandler = new ConnectionHandler(this, adapter, device, userName, connectionListener);
        connectionHandler.setUserMaxHR(maxHR);
        connectionHandler.setUserFTP(ftp);

        if (connectAsClient) {
            if (device == null) {
                Timber.tag(LOG_TAG).e("No device found in intent and connectAsClient is true");
                stopForeground(true);
                return START_NOT_STICKY;
            }
            Timber.tag(LOG_TAG).i("Starting client");
            connectionHandler.startAsClient();
        }
        else {
            Timber.tag(LOG_TAG).i("Starting server");
            connectionHandler.startAsServer();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy(){
        unregisterReceiver(adapterStateReceiver);
        super.onDestroy();
    }

    private void createNotificationChannel(){
        String notificationChannelName = "Partner Bluetooth Connection";
        NotificationChannel channel = new NotificationChannel(
                this.notificationChannelId,
                notificationChannelName,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
    }
}
