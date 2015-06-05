package com.example.lab_bt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;

public class BluetoothLeService extends Service {
    private static final String TAG = BluetoothLeService.class.getName();

    public static final String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public static final String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public static final String ACTION_GATT_SERVICE_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICE_DISCOVERED";
    public static final String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";

    public static final String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private final Binder binder = new LocalBinder();

    public BluetoothLeService() {
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICE_DISCOVERED);
                Log.i(TAG, "GATT services discovered.");
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT server connected.");
                broadcastUpdate(ACTION_GATT_CONNECTED);
                Log.i(TAG, "Start discovering GATT services.");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT server disconnected.");
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT characteristic read: " + characteristic.getStringValue(0));
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }
    };

    void broadcastUpdate(String action) {
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    void broadcastUpdate(String action, BluetoothGattCharacteristic characteristic) {
        Intent intent = new Intent(action);

        String value = characteristic.getStringValue(0);
        intent.putExtra(EXTRA_DATA, value);

        sendBroadcast(intent);
    }

    public boolean connect(final String address) {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "BluetoothAdapter not initialized.");
            return false;
        }
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.w(TAG, "Bluetooth address is invalid.");
            return false;
        }

        if (bluetoothGatt != null) {
            Log.w(TAG, "GATT server already connected.");
            return true;
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.e(TAG, "Unable to find the device.");
            return false;
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        return true;
    }

    public boolean initialize() {
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (manager == null) {
            Log.e(TAG, "Unable to initialize BluetoothManager.");
            return false;
        }

        bluetoothAdapter = manager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to get a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public BluetoothGattCharacteristic getCharacteristic(UUID serviceUuid, UUID charUuid) {
        BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
        if (service == null) {
            Log.w(TAG, String.format("Service %s not found.", serviceUuid.toString()));
            return null;
        }

        return service.getCharacteristic(charUuid);
    }

    public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
        return bluetoothGatt.readCharacteristic(characteristic);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        disconnect();
        return super.onUnbind(intent);
    }

    private void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
        bluetoothGatt = null;
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enable) {
        bluetoothGatt.setCharacteristicNotification(characteristic, enable);
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }
}
