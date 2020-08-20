package com.ble.peripheraldemo;

import android.os.IBinder;

/**
 * 手机从机代理，以单例的代理类持有服务，方便调用
 * Created by Administrator on 2017/11/21 0021.
 */

public class SlaverProxy {
    private static SlaverProxy instance;
    private PeripheralService mService;

    private SlaverProxy() {
    }

    public static SlaverProxy getInstance() {
        if (instance == null) instance = new SlaverProxy();
        return instance;
    }

    public void setService(IBinder service) {
        mService = ((PeripheralService.LocalBinder) service).getService();
    }

    public void startAdvertising(String localname, int advMode, int txPower, byte[] mfrData) {
        if (mService != null)
            mService.startAdvertising(localname, advMode, txPower, mfrData);
    }

    public void stopAdvertising() {
        if (mService != null)
            mService.stopAdvertising();
    }

    public void notifySend(byte[] data) {
        if (mService != null)
            mService.notifySend(data);
    }

    public void disconnect() {
        if (mService != null) mService.disconnect();
    }
}