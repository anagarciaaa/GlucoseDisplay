package io.hammerhead.sample;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import io.hammerhead.sample.cgm.CGMService;
import io.hammerhead.sample.cgm.CGMStore;
import io.hammerhead.sample.cgm.GlucosePacket;
import io.hammerhead.sample.cgm.SteloScanner;
import io.hammerhead.sample.service.ConnectionHandler;
import io.hammerhead.sample.service.PartnerService;
import io.hammerhead.sdk.v0.KeyValueStore;
import io.hammerhead.sdk.v0.SdkContext;
import timber.log.Timber;

import android.view.LayoutInflater;
import android.view.ViewGroup;

public class MainActivity extends BaseActivity {
    private KeyValueStore kvStore;
    private PartnerService partnerService = null;

    private static final int REQUEST_JOIN_SESSION = 1;
    private static final int REQUEST_CREATE_SESSION = 2;
    private static final int EDIT_PROFILE_ACTIVITY = 3;
    private static final String LOG_TAG = "MainActivity";

    private static class Profile {
        public String userName;
        public int ftp;
        public int maxHr;

        public Profile(String userName, int ftp, int maxHr) {
            this.userName = userName;
            this.ftp = ftp;
            this.maxHr = maxHr;
        }

        public Profile(KeyValueStore kvStore) {
            userName = kvStore.getString(KeyConstants.USER_NAME);
            if (userName == null) {
                userName = "User";
            }

            Double storedFtpVal = kvStore.getDouble(KeyConstants.USER_FTP);
            if (storedFtpVal != null) {
                ftp = storedFtpVal.intValue();
            } else {
                ftp = 140;
            }

            Double storedMaxHrVal = kvStore.getDouble(KeyConstants.USER_MAX_HR);
            if (storedMaxHrVal != null) {
                maxHr = storedMaxHrVal.intValue();
            } else {
                maxHr = 180;
            }
        }

        @Override
        public String toString() {
            return String.format("Profile: %s, %d, %d", userName, ftp, maxHr);
        }
    }

    private Profile profile;

    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Timber.tag(LOG_TAG).i("received intent: %s", intent.getAction());
            String action = intent.getAction();
            if (action != null && action.equals(PartnerService.PARTNER_NAME_RECEIVED)) {
                String partnerName = intent.getStringExtra(KeyConstants.PARTNER_NAME);
                TextView title = findViewById(R.id.title);
                title.setText("Connected to " + partnerName);
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            PartnerService.LocalBinder binder = (PartnerService.LocalBinder) iBinder;
            partnerService = binder.getService();
            ConnectionHandler.ConnectionState state = partnerService.getConnectionState();
            if (state != ConnectionHandler.ConnectionState.DISCONNECTED) {
                startConnectionActivity();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            partnerService = null;
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBaseLayout(R.layout.activity_main);

        Button joinSessionButton = findViewById(R.id.joinSessionButton);
        joinSessionButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, JoinSessionActivity.class);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(intent, REQUEST_JOIN_SESSION);
        });

        Button startSessionButton = findViewById(R.id.startSessionButton);
        startSessionButton.setOnClickListener(view -> {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            startActivityForResult(enableBtIntent, REQUEST_CREATE_SESSION);
        });

        Button editProfileButton = findViewById(R.id.editProfileButton);
        editProfileButton.setOnClickListener(view -> startEditProfileActivity());

        // --- PAIR SENSOR BUTTON ---
        Button pairSensorButton = findViewById(R.id.pairSensorButton);
        TextView cgmStatus = findViewById(R.id.cgm_status);
        SteloScanner steloScanner = new SteloScanner(this);

        pairSensorButton.setOnClickListener(view -> {
            pairSensorButton.setEnabled(false);
            cgmStatus.setText("Scanning for Stelo...");
            steloScanner.startPairingScan(new SteloScanner.PairingCallback() {
                @Override
                public void onDeviceFound(BluetoothDevice device) {
                    runOnUiThread(() -> {
                        cgmStatus.setText("Found sensor — pairing dialog should appear");
                        pairSensorButton.setEnabled(true);
                    });
                }

                @Override
                public void onScanFailed(int errorCode) {
                    runOnUiThread(() -> {
                        cgmStatus.setText("Scan failed (code " + errorCode
                                + ") — check Bluetooth and Location are on");
                        pairSensorButton.setEnabled(true);
                    });
                }

                @Override
                public void onTimeout() {
                    runOnUiThread(() -> {
                        cgmStatus.setText("No sensor found — make sure Stelo is active");
                        pairSensorButton.setEnabled(true);
                    });
                }
            });
        });

        // --- CGM TEST BUTTON ---
        Button testCgmButton = findViewById(R.id.testCgmButton);
        Handler fakeCgmHandler = new Handler(getMainLooper());
        final int[] testValues = {70, 90, 110, 145, 200, 280};
        final int[] delaysMs = {3000, 5000, 10000};
        final int[] index = {0};
        final boolean[] fakeModeRunning = {false};
        final Runnable[] fakeCgmRunnable = new Runnable[1];
        final Runnable[] disconnectRunnable = new Runnable[1];

        disconnectRunnable[0] = new Runnable() {
            @Override
            public void run() {
                ViewGroup previewContainer = findViewById(R.id.cgm_preview_container);
                if (previewContainer != null && previewContainer.getChildCount() > 0) {
                    View childView = previewContainer.getChildAt(0);
                    TextView glucosePreview = childView.findViewById(R.id.glucose_value);
                    if (glucosePreview != null) glucosePreview.setText("---");
                }

                cgmStatus.setText("CGM disconnected");
            }
        };

        fakeCgmRunnable[0] = new Runnable() {
            @Override
            public void run() {
                if (!fakeModeRunning[0]) {
                    return;
                }

                int glucose = testValues[index[0] % testValues.length];
                index[0]++;
                int trendRaw = 15;
                int predicted = glucose + 5;

                byte[] packet = new byte[19];
                packet[0] = 0x4E;
                packet[10] = 30;
                packet[11] = 0x00;
                packet[12] = (byte) (glucose & 0xFF);
                packet[13] = (byte) ((glucose >> 8) & 0xFF);
                packet[14] = 0x00;
                packet[15] = (byte) trendRaw;
                packet[16] = (byte) (predicted & 0xFF);
                packet[17] = (byte) ((predicted >> 8) & 0xFF);

                GlucosePacket parsed = GlucosePacket.parse(packet);
                int nextDelay = delaysMs[(int) (Math.random() * delaysMs.length)];
                boolean weakConnection = nextDelay == 10000;

                if (parsed != null && parsed.isUsable()) {
                    ViewGroup previewContainer = findViewById(R.id.cgm_preview_container);
                    if (previewContainer != null && previewContainer.getChildCount() > 0) {
                        View childView = previewContainer.getChildAt(0);
                        TextView glucosePreview = childView.findViewById(R.id.glucose_value);
                        if (glucosePreview != null) {
                            glucosePreview.setText(glucose + " " + parsed.trendSymbol());
                        }
                    }

                    fakeCgmHandler.removeCallbacks(disconnectRunnable[0]);
                    fakeCgmHandler.postDelayed(disconnectRunnable[0], 12000);

                    String statusText = "Preview: " + glucose + " mg/dL " + parsed.trendSymbol()
                            + "\nnext update in " + (nextDelay / 1000) + " sec";
                    if (weakConnection) {
                        statusText += "\nweak";
                    }
                    cgmStatus.setText(statusText);
                    fakeCgmHandler.postDelayed(this, nextDelay);
                } else {
                    fakeCgmHandler.removeCallbacks(disconnectRunnable[0]);
                    cgmStatus.setText("Parse failed — check packet bytes");
                    fakeModeRunning[0] = false;
                    testCgmButton.setText("Test CGM");
                }
            }
        };

        testCgmButton.setOnClickListener(view -> {
            if (fakeModeRunning[0]) {
                fakeCgmHandler.removeCallbacks(fakeCgmRunnable[0]);
                fakeCgmHandler.removeCallbacks(disconnectRunnable[0]);
                fakeModeRunning[0] = false;
                cgmStatus.setText("Fake CGM stopped");
                testCgmButton.setText("Test CGM");

                ViewGroup previewContainer = findViewById(R.id.cgm_preview_container);
                if (previewContainer != null && previewContainer.getChildCount() > 0) {
                    View childView = previewContainer.getChildAt(0);
                    TextView glucosePreview = childView.findViewById(R.id.glucose_value);
                    if (glucosePreview != null) glucosePreview.setText("---");
                }
                return;
            }

            fakeModeRunning[0] = true;
            testCgmButton.setText("Stop Test CGM");
            cgmStatus.setText("Starting fake CGM stream...");

            try {
                Intent cgmIntent = new Intent(MainActivity.this, CGMService.class);
                startForegroundService(cgmIntent);
            } catch (SecurityException e) {
                cgmStatus.setText("CGMService unavailable on emulator");
            }

            fakeCgmRunnable[0].run();
        });

        // --- TEMPORARY CGM VIEW PREVIEW ---
        ViewGroup previewContainer = findViewById(R.id.cgm_preview_container);
        if (previewContainer != null) {
            View cgmPreview = LayoutInflater.from(this).inflate(R.layout.view_cgm, previewContainer, false);
            TextView glucoseText = cgmPreview.findViewById(R.id.glucose_value);
            glucoseText.setText("145 --");
            previewContainer.addView(cgmPreview);
        }

        IntentFilter filter = new IntentFilter(PartnerService.PARTNER_NAME_RECEIVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(this.serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(this.serviceReceiver, filter);
        }

        Intent serviceIntent = new Intent(this, PartnerService.class);
        serviceIntent.setAction(PartnerService.BIND_LOCAL);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void startEditProfileActivity() {
        Intent intent = new Intent(this, EditProfileActivity.class);
        intent.putExtra(KeyConstants.USER_NAME, profile.userName);
        intent.putExtra(KeyConstants.USER_FTP, profile.ftp);
        intent.putExtra(KeyConstants.USER_MAX_HR, profile.maxHr);
        startActivityForResult(intent, EDIT_PROFILE_ACTIVITY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timber.tag(LOG_TAG).i("onResume called");
        Timber.tag(LOG_TAG).i("profile: %s", profile);

        if (profile == null) {
            profile = new Profile(SdkContext.buildSdkContext(this).getKeyValueStore());
            Timber.tag(LOG_TAG).i("Loaded profile: %s", profile);
        }

        TextView userNameView = findViewById(R.id.user_name);
        userNameView.setText(String.format("Hello %s!", profile.userName));
    }

    @Override
    protected void onStart() {
        super.onStart();
        Timber.tag(LOG_TAG).i("onStart called");

        if (partnerService != null) {
            ConnectionHandler.ConnectionState state = partnerService.getConnectionState();
            if (state != ConnectionHandler.ConnectionState.DISCONNECTED) {
                startConnectionActivity();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            kvStore = SdkContext.buildSdkContext(this).getKeyValueStore();
            kvStore.putString(KeyConstants.USER_NAME, profile.userName);
            kvStore.putDouble(KeyConstants.USER_FTP, (double) profile.ftp);
            kvStore.putDouble(KeyConstants.USER_MAX_HR, (double) profile.maxHr);
        } catch (IllegalArgumentException e) {
            Timber.w("KeyValueStore not available (emulator?) — profile not saved");
        }
    }

    private Intent createServiceIntent(boolean connectAsClient, Profile profile, BluetoothDevice device) {
        Intent serviceIntent = new Intent(this, PartnerService.class);
        serviceIntent.putExtra(KeyConstants.USER_NAME, profile.userName);
        serviceIntent.putExtra(KeyConstants.CONNECT_AS_CLIENT, connectAsClient);
        if (device != null) {
            serviceIntent.putExtra(KeyConstants.DEVICE, device);
        }
        serviceIntent.putExtra(KeyConstants.USER_MAX_HR, profile.maxHr);
        serviceIntent.putExtra(KeyConstants.USER_FTP, profile.ftp);
        return serviceIntent;
    }

    void startConnectionActivity() {
        Timber.tag(LOG_TAG).i("starting ConnectionActivity");
        Intent connectionIntent = new Intent(this, ConnectionActivity.class);
        startActivity(connectionIntent);
        finish();
    }

    void startServiceAndActivity(boolean connectAsClient, Profile profile, BluetoothDevice device) {
        Intent serviceIntent = createServiceIntent(connectAsClient, profile, device);
        Timber.tag(LOG_TAG).i("starting service");
        startForegroundService(serviceIntent);
        startConnectionActivity();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Timber.tag(LOG_TAG).i("received result from activity: %d", requestCode);

        if (requestCode != EDIT_PROFILE_ACTIVITY) {
            profile = new Profile(SdkContext.buildSdkContext(this).getKeyValueStore());
        }

        switch (requestCode) {
            case REQUEST_JOIN_SESSION:
                if (resultCode == RESULT_OK) {
                    Bundle data = intent.getExtras();
                    BluetoothDevice device = data != null ? data.getParcelable("device") : null;
                    if (device == null) {
                        Timber.tag(LOG_TAG).w("could not receive device from JoinSessionActivity");
                        return;
                    }
                    Timber.tag(LOG_TAG).i("received device from JoinSessionActivity: %s", device.getName());
                    startServiceAndActivity(true, profile, device);
                } else if (resultCode == RESULT_CANCELED) {
                    return;
                }
                break;
            case REQUEST_CREATE_SESSION:
                if (resultCode == RESULT_CANCELED) {
                    return;
                }
                Timber.tag(LOG_TAG).i("starting service (server)");
                startServiceAndActivity(false, profile, null);
                break;
            case EDIT_PROFILE_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    String userName = intent.getStringExtra(KeyConstants.USER_NAME);
                    int ftp = intent.getIntExtra(KeyConstants.USER_FTP, 0);
                    int maxHr = intent.getIntExtra(KeyConstants.USER_MAX_HR, 0);
                    profile = new Profile(userName, ftp, maxHr);
                } else {
                    Timber.tag(LOG_TAG).e("EditProfileActivity returned with resultCode: %d", resultCode);
                }
                break;
        }
    }
}
