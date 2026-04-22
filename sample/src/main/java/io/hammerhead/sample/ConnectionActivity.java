package io.hammerhead.sample;

import io.hammerhead.sample.service.ConnectionHandler.ConnectionState;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import io.hammerhead.sample.service.PartnerService;
import timber.log.Timber;

//As of writing, MainActivity is the only activity that starts this activity,
//and MainActivity will always call finish() after starting this activity.
public class ConnectionActivity extends BaseActivity {
    private final String LOG_TAG = "ConnectionActivity";
    private PartnerService partnerService = null;
    private ConnectionState curState = ConnectionState.CONNECTING;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Timber.tag(LOG_TAG).i("service connected");
            PartnerService.LocalBinder binder = (PartnerService.LocalBinder) iBinder;
            partnerService = binder.getService();
            curState = partnerService.getConnectionState();
            updateUIFromState(curState);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Timber.tag(LOG_TAG).i("service disconnected");
            partnerService = null;
        }
    };

    public final BroadcastReceiver connStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            String action = intent.getAction();
            if(action == null){
                Timber.tag(LOG_TAG).e("received null action");
                return;
            }
            if(action.equals(PartnerService.CONNECTION_STATE_CHANGED)){
                ConnectionState state = (ConnectionState) intent.getSerializableExtra(KeyConstants.CONNECTION_STATE);
                Timber.tag(LOG_TAG).i("received connection state: %s", state.name());
                updateUIFromState(state);
            }
            else if(action.equals(PartnerService.PARTNER_NAME_RECEIVED)){
                Timber.tag(LOG_TAG).i("received partner name");
                updateUIFromState(curState);
            }
            else{
                Timber.tag(LOG_TAG).e("received unknown action: %s", action);
            }
        }
    };

    private void updateUIFromState(ConnectionState state){
        Timber.tag(LOG_TAG).i("received connection state: %s", state);
        if(curState == ConnectionState.CONNECTING && state == ConnectionState.RETRYING){
            //If the connectionHandler is just retrying the initial connection, don't update the UI
            Timber.tag(LOG_TAG).i("ignoring retrying state");
            return;
        }
        curState = state;
        switch(state){
            case CONNECTING:
                onConnecting(false);
                break;
            case CONNECTED:
                onConnected();
                break;
            case RETRYING:
                //TODO: change to say reconnecting
                onConnecting(true);
                break;
            case DISCONNECTED:
                onDisconnected();
                break;
        }
    }
    private void onConnecting(boolean reconnecting){
        String text = reconnecting ? "Reconnecting..." : "Connecting...";
        Timber.tag(LOG_TAG).i("connecting to partner");
        TextView statusText = findViewById(R.id.connectionStatus);
        statusText.setText(text);
        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setText("CANCEL");
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void onConnected(){
        Timber.tag(LOG_TAG).i("connected to partner");
        TextView statusText = findViewById(R.id.connectionStatus);
        String partnerName = partnerService != null ? partnerService.getPartnerName() : null;
        if(partnerName != null){
            statusText.setText("Connected to " + partnerName);
        }
        else{
            statusText.setText("Connected");
        }
        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setText("DISCONNECT");
        ProgressBar progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);
    }

    private void onDisconnected(){
        Timber.tag(LOG_TAG).i("disconnected from partner");
        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        startActivity(mainActivityIntent);
        finish();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBaseLayout(R.layout.activity_connection);
        Button disconnectButton = findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Timber.tag(LOG_TAG).i("disconnecting from partner");
                partnerService.disconnect();
            }
        });

        IntentFilter filter = new IntentFilter(PartnerService.CONNECTION_STATE_CHANGED);
        filter.addAction(PartnerService.PARTNER_NAME_RECEIVED);
        registerReceiver(connStateReceiver, filter);

        Intent bindIntent = new Intent(this, PartnerService.class);
        bindIntent.setAction(PartnerService.BIND_LOCAL);
        bindService(bindIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(connStateReceiver);
        super.onDestroy();
    }
}
