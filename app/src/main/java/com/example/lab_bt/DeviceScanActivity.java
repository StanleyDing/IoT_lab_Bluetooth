package com.example.lab_bt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class DeviceScanActivity extends AppCompatActivity {
    private static final String TAG = DeviceScanActivity.class.getName();
    private static final long SCAN_PERIOD = 10000;
    private static final int REQUEST_ENABLE_BT = 1;

    private boolean isScanning;
    private Handler handler;

    private ListView scanList;
    private DeviceListAdapter deviceListAdapter;

    private Switch autoConnectSwitch;
    private AcceptThread acceptThread;

    private BluetoothAdapter bluetoothAdapter;

    private final BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    deviceListAdapter.addDevice(device);
                    if (autoConnectSwitch.isChecked() && device.getName().equals("DSSCSA_8")) {
                        scanLeDevice(false);
                        connectDevice(device);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceListAdapter.addDevice(device);
                            deviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    private final AdapterView.OnItemClickListener itemClickListener =
            new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view,
                                        int position, long id) {
                    BluetoothDevice device = deviceListAdapter.getDevice(position);
                    if (device == null) {
                        return;
                    }

                    connectDevice(device);
                }
            };

    private void connectDevice(BluetoothDevice device) {
        Intent intent = new Intent(DeviceScanActivity.this,
                DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS,
                device.getAddress());
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);

        handler = new Handler();
        scanList = (ListView) findViewById(R.id.scan_list);
        deviceListAdapter = new DeviceListAdapter();

        autoConnectSwitch = (Switch) findViewById(R.id.auto_connect_switch);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.bluetooth_le_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Toast.makeText(this, R.string.bluetooth_service_not_found, Toast.LENGTH_SHORT).show();
            finish();
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }

        scanList.setAdapter(deviceListAdapter);
        scanList.setOnItemClickListener(itemClickListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        scanLeDevice(false);
        deviceListAdapter.clear();
        if (acceptThread != null)
            acceptThread.cancel();
    }

    private void scanLeDevice(boolean enable) {
        if (enable) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanLeDevice(false);
                }
            }, SCAN_PERIOD);

            isScanning = true;
            bluetoothAdapter.startLeScan(leScanCallback);
            Log.i(TAG, "Start LE scan.");
        } else {
            isScanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);
            Log.i(TAG, "Stop LE scan.");
        }
        invalidateOptionsMenu();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        if (isScanning) {
            menu.findItem(R.id.action_scan).setVisible(false);
        } else {
            menu.findItem(R.id.action_scan).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_scan) {
            scanLeDevice(true);
            return true;
        } else if (id == R.id.action_receive_key) {
            if (acceptThread == null) {
                acceptThread = new AcceptThread();
                acceptThread.start();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private BluetoothSocket socket = null;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                Log.d(TAG, "Listening socket...");
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(App.NAME, App.MY_UUID_SECURE);
            } catch (IOException e) { e.printStackTrace(); }
            mmServerSocket = tmp;
        }

        public void run() {
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                    Log.d(TAG, "Socket accepted!");
                } catch (IOException e) {
                    return;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }

            try {
                InputStream inputStream = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                OutputStream outputStream = socket.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));

                String token = reader.readLine();
                Log.d(TAG, "Token: " + token);

                writer.write("GET");
                writer.newLine();
                writer.flush();

                String response = reader.readLine();
                Log.d(TAG, "Client response: " + response);
                if (response.equals("DONE")) {
                    // todo: add token
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (socket != null) {
                        socket.close();
                        Log.d(TAG, "Socket closed after reading.");
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                if (mmServerSocket != null)
                    mmServerSocket.close();
                Log.d(TAG, "Server socket closed on cancel.");
            } catch (IOException e) { e.printStackTrace(); }

            try {
                if (socket != null) {
                    socket.close();
                    Log.d(TAG, "Socket closed on cancel.");
                }
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
        private LayoutInflater inflater;

        public DeviceListAdapter() {
            inflater = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!bluetoothDevices.contains(device)) {
                bluetoothDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return bluetoothDevices.get(position);
        }

        public void clear() {
            bluetoothDevices.clear();
        }

        @Override
        public int getCount() {
            return bluetoothDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return bluetoothDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            DeviceViewHolder viewHolder;

            if (view == null) {
                view = inflater.inflate(R.layout.listitem_device, null);
                viewHolder = new DeviceViewHolder();
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                view.setTag(viewHolder);
            } else {
                viewHolder = (DeviceViewHolder) view.getTag();
            }

            BluetoothDevice device = getDevice(i);
            String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);
            }
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }

        private class DeviceViewHolder {
            private TextView deviceName;
            private TextView deviceAddress;
        }
    }
}
