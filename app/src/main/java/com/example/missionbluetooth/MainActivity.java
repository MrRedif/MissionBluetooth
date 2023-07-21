package com.example.missionbluetooth;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {
    private static final String TAG = "MainActivity";
    BluetoothAdapter mBlueToothAdapter;
    Button buttonBluetooth;
    Button buttonDiscover;

    TextView textDataView;
    public ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    DeviceListAdapter deviceListAdapter;
    ListView lvNewDevices;
    BluetoothDevice bondedDevice;

    byte[] packet = new byte[0];

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
                    if (service.getUuid().equals(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"))){
                        listenToService(gatt, service);
                    }
                }
            } else {
                // Service discovery failed
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();

            if (data.length == 0) return;

            //Add to packet stream
            byte[] result = new byte[packet.length + data.length];
            System.arraycopy(packet, 0, result, 0, packet.length);
            System.arraycopy(data, 0, result, packet.length, data.length);
            packet = result;

            //Procces if end flag
            if (data.length >= 2 && data[data.length - 1] == 0x0A && data[data.length - 2] == 0x0D)//Packet end
            {
                proccesPacket();
                packet = new byte[0];
            }
        }

    };

    void proccesPacket(){
        String sentence = new String(packet, StandardCharsets.US_ASCII);
        if (sentence.startsWith("$GPGGA") || sentence.startsWith("$GNRMC")){
            String[] gpggaPacket = sentence.split(",");
            String parsedPackage =
                    "Time: " + gpggaPacket[1] +
                    "\nLat: " + formatStringPersicion(gpggaPacket[2])  + " " + gpggaPacket[3] +
                    "\nLong: " + formatStringPersicion(gpggaPacket[4].substring(1)) + " " + gpggaPacket[5] +
                    "\nQuality: " + gpggaPacket[6];

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Update UI or perform other tasks on the main thread
                    textDataView.setText((parsedPackage));
                }
            });
        }

        //Battery Info
        if (packet.length == 6 && packet[0] == 0x05){
            String batteryPacked = String.format("%8s", Integer.toBinaryString(packet[1] & 0xFF)).replace(' ', '0');
            char flag = batteryPacked.charAt(batteryPacked.length()-1);
            String battery = "0" + batteryPacked.substring(0,batteryPacked.length()-2);
            int chargePercentage =  Integer.parseInt(battery,2);

            Log.d(TAG, "Battery: " + chargePercentage + " Charge Status: " + flag);
        }
    }

    public static String formatStringPersicion(String inputString) {
        String replaced = (inputString.replace(".", ""));
        String formattedString = replaced.substring(0, 2) + "." + replaced.substring(2);
        return formattedString;
    }

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
        textDataView = findViewById(R.id.textData);

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

    @SuppressLint("MissingPermission")
    private void listenToService(BluetoothGatt gatt, BluetoothGattService service) {
        UUID targetCharacteristicUUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
        BluetoothGattCharacteristic targetCharacteristic = service.getCharacteristic(targetCharacteristicUUID);

        if (targetCharacteristic != null) {
            boolean enableNotification = true;

            gatt.setCharacteristicNotification(targetCharacteristic, enableNotification);
            BluetoothGattDescriptor descriptor = targetCharacteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")); // Client Characteristic Configuration descriptor UUID

            if (descriptor != null) {
                descriptor.setValue(enableNotification ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE :
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);

                gatt.writeDescriptor(descriptor);
            }
        } else {
            // Target characteristic not found, handle the situation if needed
        }
    }

    public static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
            sb.append(" ");
        }
        return sb.toString();
    }
}