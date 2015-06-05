package com.example.lab_bt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class DeviceScanActivity extends AppCompatActivity {
    private static final String TAG = DeviceScanActivity.class.getName();
    private static final long SCAN_PERIOD = 10000;
    private static final int REQUEST_ENABLE_BT = 1;

    private boolean isScanning;
    private Handler handler;

    private ListView scanList;
    private DeviceListAdapter deviceListAdapter;

    private BluetoothAdapter bluetoothAdapter;

    private final BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    deviceListAdapter.addDevice(device);
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

                    Intent intent = new Intent(DeviceScanActivity.this,
                            DeviceControlActivity.class);
                    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS,
                            device.getAddress());
                    startActivity(intent);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scan);

        handler = new Handler();
        scanList = (ListView) findViewById(R.id.scan_list);
        deviceListAdapter = new DeviceListAdapter();

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
        scanLeDevice(true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        scanLeDevice(false);
        deviceListAdapter.clear();
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
        }

        return super.onOptionsItemSelected(item);
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
