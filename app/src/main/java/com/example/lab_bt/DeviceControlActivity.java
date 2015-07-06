package com.example.lab_bt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.UUID;

public class DeviceControlActivity extends AppCompatActivity {
    private static final String TAG = DeviceControlActivity.class.getName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private static final UUID serviceUuid = UUID.fromString(GattAttribute.HM10_SERVICE);
    private static final UUID charUuid = UUID.fromString(GattAttribute.HM10_CHARACTERISTIC);
    private static final int REQUEST_CONNECT = 1;

    private String deviceAddress;

    private TextView connectionState;
    private TextView ledState;
    private Switch ledSwitch;

    private BluetoothLeService bluetoothLeService;
    private BluetoothGattCharacteristic characteristic;

    private SharedPreferences settings;
    private boolean authorized;

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
                        bluetoothLeService.setCharacteristicNotification(characteristic, true);
                    }
                    break;
                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                    String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                    processData(data);
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

        settings = getPreferences(MODE_PRIVATE);
        authorized = settings.getBoolean(deviceAddress, false);

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
        if (giveKey != null)
            giveKey.abort();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_device_control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_give_key) {
            Intent intent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(intent, REQUEST_CONNECT);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private GiveKey giveKey;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONNECT) {
            if (resultCode == Activity.RESULT_OK) {
                giveKey = new GiveKey(data);
                giveKey.execute();
            }
        }
    }

    private void processData(String data) {
        if (data.equals("On")) {
            updateLedState(R.string.on);
            ledSwitch.setChecked(true);

        } else if (data.equals("Off")) {
            updateLedState(R.string.off);
            ledSwitch.setChecked(false);

        } else if (data.equals("Auth")) {
            authorized = true;
            settings.edit().putBoolean(deviceAddress, true).apply();
            ledSwitch.setEnabled(true);
            Log.d(TAG, "Authorized.");
        } else if (data.equals("Ready")) {
            ledSwitch.setEnabled(authorized);
        }
    }

    private void initLedSwitch() {
        ledSwitch = (Switch) findViewById(R.id.led_switch);
        ledSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Switch ledSwitch = (Switch) view;
                if (ledSwitch.isChecked()) {
                    characteristic.setValue("On");
                } else {
                    characteristic.setValue("Off");
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

    private class GiveKey extends AsyncTask<Void, Void, Void> {
        private String address;
        private BluetoothDevice device;
        private BluetoothSocket socket;

        private GiveKey(Intent data) {
            address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
            try {
                socket = device.createRfcommSocketToServiceRecord(App.MY_UUID_SECURE);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Socket create failed.");
                return null;
            }

            try {
                socket.connect();
                OutputStream outputStream = socket.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));

                InputStream inputStream = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                writer.write(deviceAddress);
                writer.newLine();
                writer.flush();

                String response = reader.readLine();
                Log.d(TAG, "Server response: " + response);
                if (response.equals("GET")) {
                    writer.write("DONE");
                    writer.newLine();
                    writer.flush();
                }
                while (socket.isConnected());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Lost connection.");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        private void abort() {
            Log.d(TAG, "ABORT!!");
        }
    }
}