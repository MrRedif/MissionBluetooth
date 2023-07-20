package com.example.missionbluetooth;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = "MainActivity";
    BluetoothAdapter mBlueToothAdapter;
    Button buttonBluetooth;
    Button buttonDiscover;
    public ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    DeviceListAdapter deviceListAdapter;
    ListView lvNewDevices;
    BluetoothDevice bondedDevice;


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Device connected
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Device disconnected
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // GATT services discovered
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    Log.d(TAG,"Service Found: " + service.getUuid().toString());
                }
            } else {
                // Service discovery failed
            }
        }

    };

    private final BroadcastReceiver recieverStateChange = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "$STATE_RECIEVER: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "$STATE_RECIEVER: STATE TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "$STATE_RECIEVER:  STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "$STATE_RECIEVER: STATE TURNING ON");
                        break;
                }

            }
        }
    };

    private final BroadcastReceiver recieverDiscoverAction = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                Log.d(TAG, "FOUND DEVICE!");
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (!bluetoothDevices.contains(device)) bluetoothDevices.add(device);
                Log.d(TAG, "Discovered " + device.getName() + ": " + device.getAddress());
                deviceListAdapter = new DeviceListAdapter(context, R.layout.device_adapter_view, bluetoothDevices);

                lvNewDevices.setAdapter(deviceListAdapter);
            }

        }
    };


    private final BroadcastReceiver recieverBond = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "$BOND_RECIEVER: BOND_BONDED");
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "$BOND_RECIEVER: BOND_BONDING");
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "$BOND_RECIEVER: BOND_NONE");
                        break;
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(recieverStateChange);
        unregisterReceiver(recieverDiscoverAction);
        unregisterReceiver(recieverBond);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPerms();

        //Init lists
        bluetoothDevices = new ArrayList<>();

        //Define adapter
        mBlueToothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();


        //Find Elements
        buttonBluetooth = (Button) findViewById(R.id.btnOnOff);
        buttonDiscover = (Button) findViewById(R.id.btnDiscover);
        lvNewDevices = (ListView) findViewById(R.id.lvNewDevices);


        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(recieverBond, filter);


        //Add event listeners
        buttonBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothSwitch();
            }
        });

        buttonDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothDiscover();
            }
        });


        lvNewDevices.setOnItemClickListener(MainActivity.this);


    }

    @SuppressLint("MissingPermission")
    public void bluetoothSwitch() {
        if (mBlueToothAdapter == null) {
            Log.d(TAG, "Does not have Bluetooth capabilities");
        } else if (!mBlueToothAdapter.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityIfNeeded(enableBTIntent, 0);
            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(recieverStateChange, BTIntent);
        } else {
            mBlueToothAdapter.disable();
            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(recieverStateChange, BTIntent);
            Log.d(TAG, "Deactivating BLUETOOTH");
        }
    }

    @SuppressLint("MissingPermission")
    public void bluetoothDiscover() {
        Log.d(TAG, "[LOOKING FOR UNPAIRED DEVICES!]");

        if (mBlueToothAdapter.isDiscovering()) {
            mBlueToothAdapter.cancelDiscovery();
            Log.d(TAG, "Canceling discovery.");

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(recieverDiscoverAction, filter);
            mBlueToothAdapter.startDiscovery();
        } else if (!mBlueToothAdapter.isDiscovering()) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(recieverDiscoverAction, filter);
            mBlueToothAdapter.startDiscovery();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        mBlueToothAdapter.cancelDiscovery();
        String dName = bluetoothDevices.get(i).getName();
        Log.d(TAG,"onItemClick: You clicked on device" + dName);

        BluetoothDevice device = bluetoothDevices.get(i);
        device.connectGatt(this , false , gattCallback, TRANSPORT_LE );
        bondedDevice = device;
    }

    public boolean checkPerms() throws SecurityException{
        int perms = this.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN);
        perms += this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        perms += this.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);
        perms += this.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT);
        perms += this.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE);
        if (perms != 0) {
            this.requestPermissions(new String[]{
                            android.Manifest.permission.BLUETOOTH_SCAN,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                    },

                    99);
            return false;
        }
        return true;
    }
}