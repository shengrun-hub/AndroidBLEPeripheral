package com.ble.peripheraldemo;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 手机作从机
 * Created by Administrator on 2017/8/23 0023.
 */
public class PeripheralService extends Service {
    private static final String TAG = "PeripheralService";

    public static final String ACTION_WRITE_REQUEST = "PeripheralService.ACTION_WRITE_REQUEST";
    public static final String ACTION_CONNECTED = "PeripheralService.ACTION_CONNECTED";
    public static final String ACTION_DISCONNECTED = "PeripheralService.ACTION_DISCONNECTED";
    public static final String EXTRA_ADDRESS = "PeripheralService.EXTRA_ADDRESS";
    public static final String EXTRA_DATA = "PeripheralService.EXTRA_DATA";

    // 自定义UUID
    private static final String SERVICE_UUID = "00001000-0000-1000-8000-00805f9b34fb";
    private static final String[] CHARACTERISTIC_UUID_ARRAY = {
            "00001001-0000-1000-8000-00805f9b34fb",
            "00001002-0000-1000-8000-00805f9b34fb",
            "00001003-0000-1000-8000-00805f9b34fb",
            "00001004-0000-1000-8000-00805f9b34fb",
            "00001005-0000-1000-8000-00805f9b34fb",
    };

    // 以下两个UUID是行业标准，见 https://www.bluetooth.com/specifications/gatt/descriptors/
    private static final UUID Characteristic_User_Description = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");
    private static final UUID Client_Characteristic_Configuration = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mLeAdvertiser;
    private BluetoothDevice mDevice;


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        Log.i(TAG, "手机蓝牙开启");
                        initialize();
                        break;

                    case BluetoothAdapter.STATE_OFF:
                        Log.w(TAG, "手机蓝牙关闭");
                        stopAdvertising();
                        closeGattServer();
                        break;
                }
            }
        }
    };


    /**
     * 断开连接
     */
    void disconnect() {
        if (mDevice != null && mBluetoothGattServer != null) {
            mBluetoothGattServer.cancelConnection(mDevice);
        }
    }


    /**
     * 开启从机广播
     */
    public void startAdvertising(String localname, int advMode, int txPower, byte[] mfrData) {
        if (mLeAdvertiser == null) {
            Log.e(TAG, "startAdvertising() - mLeAdvertiser is null");
            return;
        }

        BluetoothAdapter adapter = mBluetoothManager.getAdapter();
        if (!adapter.isEnabled()) {
            Log.e(TAG, "startAdvertising() - Bluetooth disabled");
            return;
        }

        if (!initGattServer()) {
            Log.e(TAG, "startAdvertising() - initGattServer failed");
            return;
        }

        if (localname != null && localname.trim().length() > 0) {
            adapter.setName(localname);
        } else {
            adapter.setName(Build.MODEL);
        }

        AdvertiseSettings.Builder settingBuilder = new AdvertiseSettings.Builder();
        settingBuilder.setConnectable(true);
        settingBuilder.setAdvertiseMode(advMode);
        settingBuilder.setTxPowerLevel(txPower);
        settingBuilder.setTimeout(0);

        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.addServiceUuid(ParcelUuid.fromString(SERVICE_UUID));
        dataBuilder.setIncludeDeviceName(true);
        dataBuilder.setIncludeTxPowerLevel(false);

        if (mfrData != null && mfrData.length > 0) {
            //加载厂商数据
            int mfrId;
            byte[] mfrSpecificData = {};//不能给null
            if (mfrData.length == 1) {
                mfrId = mfrData[0] & 0xff;
            } else if (mfrData.length == 2) {
                mfrId = mfrId(mfrData[0], mfrData[1]);
            } else {
                mfrId = mfrId(mfrData[0], mfrData[1]);
                mfrSpecificData = Arrays.copyOfRange(mfrData, 2, mfrData.length);
            }
            dataBuilder.addManufacturerData(mfrId, mfrSpecificData);
        }
        mLeAdvertiser.startAdvertising(settingBuilder.build(), dataBuilder.build(), mAdvertiseCallback);
    }

    private int mfrId(byte low, byte hig) {
        return ((hig & 0xff) << 8) + (low & 0xff);
    }


    /**
     * 关闭手机广播
     */
    public void stopAdvertising() {
        if (mLeAdvertiser != null) {
            mLeAdvertiser.stopAdvertising(mAdvertiseCallback);
        }
    }


    /**
     * 广播事件回调
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Map<String, Object> params = new HashMap<>();
            params.put("Connectable", settingsInEffect.isConnectable());
            params.put("Mode", settingsInEffect.getMode());
            params.put("Timeout", settingsInEffect.getTimeout());
            params.put("TxPowerLevel", settingsInEffect.getTxPowerLevel());

            log("onStartSuccess", params);
        }

        @Override
        public void onStartFailure(int errorCode) {
            String errorMsg = "Unknown Error";
            switch (errorCode) {
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    errorMsg = "Already started";
                    break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    errorMsg = "Data too large";
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    errorMsg = "Unsupported";
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    errorMsg = "Internal error";
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    errorMsg = "Too many advertisers";
                    break;
            }
            Log.e(TAG, "onStartFailure() - " + errorMsg);
        }
    };

    class LocalBinder extends Binder {
        PeripheralService getService() {
            return PeripheralService.this;
        }
    }

    private LocalBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        initialize();
    }

    private void initialize() {
        BluetoothAdapter adapter = mBluetoothManager.getAdapter();
        Log.e(TAG, "initialize() - isMultipleAdvertisementSupported=" + adapter.isMultipleAdvertisementSupported());
        if (adapter.isEnabled()) {
            mLeAdvertiser = adapter.getBluetoothLeAdvertiser();
            mBluetoothGattServer = mBluetoothManager.openGattServer(getApplicationContext(), mBluetoothGattServerCallback);
            adapter.setName(Build.MODEL);
        }
    }

    private boolean initGattServer() {
        if (mBluetoothGattServer == null) return false;

        try {
            mBluetoothGattServer.clearServices();

            BluetoothGattService service = new BluetoothGattService(UUID.fromString(SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY);

            for (int i = 0; i < CHARACTERISTIC_UUID_ARRAY.length; i++) {
                int properties = 0;
                int permissions = 0;
                String userDescription = null;
                switch (i) {
                    case 0:
                        properties = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                                | BluetoothGattCharacteristic.PROPERTY_WRITE
                                | BluetoothGattCharacteristic.PROPERTY_READ
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY;

                        permissions = BluetoothGattCharacteristic.PERMISSION_WRITE
                                | BluetoothGattCharacteristic.PERMISSION_READ;

                        userDescription = "RX";
                        break;
                    case 1:
                        properties = BluetoothGattCharacteristic.PROPERTY_READ
                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY;

                        permissions = BluetoothGattCharacteristic.PERMISSION_READ;
                        userDescription = "TX";
                        break;
                    case 2:
                        properties = BluetoothGattCharacteristic.PROPERTY_WRITE;
                        permissions = BluetoothGattCharacteristic.PERMISSION_WRITE;
                        userDescription = "REG_WRITE";
                        break;
                    case 3:
                        properties = BluetoothGattCharacteristic.PROPERTY_READ;
                        permissions = BluetoothGattCharacteristic.PERMISSION_READ;
                        userDescription = "REG_READ";
                        break;
                    case 4:
                        properties = BluetoothGattCharacteristic.PROPERTY_WRITE
                                | BluetoothGattCharacteristic.PROPERTY_READ;
                        permissions = BluetoothGattCharacteristic.PERMISSION_WRITE
                                | BluetoothGattCharacteristic.PERMISSION_READ;
                        userDescription = "REG";
                        break;
                }

                BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString(CHARACTERISTIC_UUID_ARRAY[i]), properties, permissions);
                BluetoothGattDescriptor userDescriptionDescriptor = new BluetoothGattDescriptor(Characteristic_User_Description, BluetoothGattDescriptor.PERMISSION_READ);
                userDescriptionDescriptor.setValue(userDescription.getBytes());
                characteristic.addDescriptor(userDescriptionDescriptor);

                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == BluetoothGattCharacteristic.PROPERTY_NOTIFY) {
                    BluetoothGattDescriptor clientConfigurationDescriptor = new BluetoothGattDescriptor(Client_Characteristic_Configuration,
                            BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
                    characteristic.addDescriptor(clientConfigurationDescriptor);
                }

                service.addCharacteristic(characteristic);
            }
            mBluetoothGattServer.addService(service);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        super.unbindService(conn);
    }

    @Override
    public void onDestroy() {
        disconnect();
        closeGattServer();
        stopAdvertising();
        unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    private void closeGattServer() {
        if (mBluetoothGattServer != null) {
            Log.e(TAG, "closeGattServer()");
            mBluetoothGattServer.close();
            mBluetoothGattServer = null;
        }
    }


    /**
     * 向主机发送数据
     */
    public void notifySend(byte[] data) {
        if (mBluetoothGattServer == null || mDevice == null || data == null) return;
        try {
            BluetoothGattService service = mBluetoothGattServer.getService(UUID.fromString(SERVICE_UUID));
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID_ARRAY[1]));
                if (characteristic != null) {
                    characteristic.setValue(data);
                    mBluetoothGattServer.notifyCharacteristicChanged(mDevice, characteristic, false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 蓝牙交互事件回调
     */
    private final BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Map<String, Object> params = new HashMap<>();
            params.put("name", "" + device.getName());
            params.put("address", "" + device.getAddress());
            params.put("status", status);
            params.put("newState", newState);
            log("onConnectionStateChange", params);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, device.getAddress() + "已连接");
                mDevice = device;
                //todo 连接成功后，需要调用connect，否则调用cancelConnection断不开
                mBluetoothGattServer.connect(device, false);
                updateBroadcast(device.getAddress(), ACTION_CONNECTED);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, device.getAddress() + "已断开");
                updateBroadcast(device.getAddress(), ACTION_DISCONNECTED);
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Map<String, Object> params = new HashMap<>();
            params.put("status", status);
            params.put("uuid", service.getUuid().toString());
            log("onServiceAdded", params);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            Map<String, Object> params = new HashMap<>();
            params.put("address", device.getAddress());
            params.put("requestId", requestId);
            params.put("offset", offset);
            params.put("uuid", characteristic.getUuid().toString());
            log("onCharacteristicReadRequest", params);
            mBluetoothGattServer.sendResponse(device, requestId, 0, offset, "ReadRequest".getBytes());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Map<String, Object> params = new HashMap<>();
            params.put("address", device.getAddress());
            params.put("requestId", requestId);
            params.put("uuid", characteristic.getUuid().toString());
            params.put("preparedWrite", preparedWrite);
            params.put("responseNeeded", responseNeeded);
            params.put("offset", offset);
            params.put("value", Util.byteArrayToHex(value));
            log("onCharacteristicWriteRequest", params);

            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, 0, offset, value);
            }
            updateBroadcast(device.getAddress(), value);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            Map<String, Object> params = new HashMap<>();
            params.put("address", device.getAddress());
            params.put("requestId", requestId);
            params.put("offset", offset);
            params.put("uuid", descriptor.getUuid().toString());
            log("onDescriptorReadRequest", params);

            mBluetoothGattServer.sendResponse(device, requestId, 0, offset, descriptor.getValue());
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Map<String, Object> params = new HashMap<>();
            params.put("address", device.getAddress());
            params.put("requestId", requestId);
            params.put("uuid", descriptor.getUuid().toString());
            params.put("preparedWrite", preparedWrite);
            params.put("responseNeeded", responseNeeded);
            params.put("offset", offset);
            params.put("value", Util.byteArrayToHex(value));
            log("onDescriptorWriteRequest", params);

            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, 0, offset, value);
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            Map<String, Object> params = new HashMap<>();
            params.put("address", device.getAddress());
            params.put("requestId", requestId);
            params.put("execute", execute);
            log("onExecuteWrite", params);

            mBluetoothGattServer.sendResponse(device, requestId, 0, 0, null);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            Map<String, Object> params = new HashMap<>();
            params.put("address", device.getAddress());
            params.put("status", status);
            log("onNotificationSent", params);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Map<String, Object> params = new HashMap<>();
            params.put("address", device.getAddress());
            params.put("mtu", mtu);
            log("onMtuChanged", params);
        }
    };

    private void updateBroadcast(String address, String action) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_ADDRESS, address);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void updateBroadcast(String address, byte[] value) {
        Intent intent = new Intent(ACTION_WRITE_REQUEST);
        intent.putExtra(EXTRA_ADDRESS, address);
        intent.putExtra(EXTRA_DATA, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void log(String methodName, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            sb.append(entry.getKey())
                    .append('=')
                    .append(entry.getValue().toString());
            if (i < params.size() - 1) {
                sb.append(", ");
            }
            i++;
        }

        String logStr = methodName + "() - " + sb.toString();
        Log.i(TAG, logStr);
    }
}