package com.example.lab_bt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import java.util.UUID;

public class DeviceControlActivity extends Activity {
    private static final String TAG = DeviceControlActivity.class.getName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static final UUID serviceUuid = UUID.fromString(GattAttribute.HM10_SERVICE);
    private static final UUID charUuid = UUID.fromString(GattAttribute.HM10_CHARACTERISTIC);

    private String deviceAddress;

    private TextView connectionState;
    private TextView ledState;
    private Switch ledSwitch;

    private BluetoothLeService bluetoothLeService;
    private BluetoothGattCharacteristic characteristic;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            bluetoothLeService = ((BluetoothLeService.LocalBinder)iBinder).getService();
            if (!bluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize BluetoothLeService.");
                finish();
            }
            bluetoothLeService.connect(deviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case BluetoothLeService.ACTION_GATT_CONNECTED:
                    updateConnectionState(R.string.connected);
                    Log.i(TAG, "Bluetooth device connected.");
                    break;
                case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                    updateConnectionState(R.string.disconnected);
                    Log.i(TAG, "Bluetooth device disconnected.");
                    break;
                case BluetoothLeService.ACTION_GATT_SERVICE_DISCOVERED:
                    characteristic = bluetoothLeService.getCharacteristic(serviceUuid, charUuid);
                    if (characteristic != null) {
                        ledSwitch.setEnabled(true);
                        bluetoothLeService.setCharacteristicNotification(characteristic, true);
                        // query led state
                        characteristic.setValue("?");
                        bluetoothLeService.writeCharacteristic(characteristic);
                    }
                    break;
                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                    String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                    if (data.equals("On")) {
                        updateLedState(R.string.on);
                        ledSwitch.setChecked(true);
                    } else if (data.equals("Off")) {
                        updateLedState(R.string.off);
                        ledSwitch.setChecked(false);
                    }
                    Log.i(TAG, "Data read: " + data);
                    break;
                default:
                    break;
            }
        }
    };

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectionState.setText(resourceId);
            }
        });
    }

    private void updateLedState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ledState.setText(resourceId);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);

        Intent intent = getIntent();
        String deviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        deviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        ((TextView) findViewById(R.id.device_name)).setText(deviceName);
        ((TextView) findViewById(R.id.device_address)).setText(deviceAddress);
        connectionState = (TextView) findViewById(R.id.connection_state);
        ledState = (TextView) findViewById(R.id.led_state);

        initLedSwitch();

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bluetoothLeService != null) {
            if (!bluetoothLeService.connect(deviceAddress)) {
                Log.w(TAG, "Fail to connect bluetooth device.");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

    private void initLedSwitch() {
        ledSwitch = (Switch) findViewById(R.id.led_switch);
        ledSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Switch ledSwitch = (Switch) view;
                if (ledSwitch.isChecked()) {
                    characteristic.setValue("O");
                } else {
                    characteristic.setValue("X");
                }
                bluetoothLeService.writeCharacteristic(characteristic);
            }
        });
    }

    private IntentFilter makeGattUpdateIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        filter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        filter.addAction(BluetoothLeService.ACTION_GATT_SERVICE_DISCOVERED);
        filter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return filter;
    }
}
