package com.ble.peripheraldemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;


/**
 * 手机做从机
 * Created by Administrator on 2017/11/21 0021.
 */

public class SlaverFragment extends Fragment {
    private SlaverProxy mSlaverProxy;

    private Spinner mTxPowerSpinner;
    private Spinner mAdvModeSpinner;
    private EditText mEdtLocalName;
    private EditText mEdtMfrData;
    private EditText mEdtTxData;
    private ListView mRxDataList;
    private ArrayAdapter<String> mRxDataListAdapter;
    private TextView mConnectionState;

    private BroadcastReceiver mGattReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case PeripheralService.ACTION_WRITE_REQUEST:
                    //todo 收到数据
                    byte[] data = intent.getByteArrayExtra(PeripheralService.EXTRA_DATA);
                    mRxDataListAdapter.add(Util.timestamp("HH:mm:ss.SSS - ") + Util.byteArrayToHex(data));
                    mRxDataList.setSelection(mRxDataListAdapter.getCount() - 1);
                    break;

                case PeripheralService.ACTION_CONNECTED:
                    String address = intent.getStringExtra(PeripheralService.EXTRA_ADDRESS);
                    mConnectionState.setText("已连接[" + address + ']');
                    mConnectionState.setTextColor(0xff35b056);
                    break;

                case PeripheralService.ACTION_DISCONNECTED:
                    mConnectionState.setText("已断开");
                    mConnectionState.setTextColor(Color.RED);
                    break;
            }
        }
    };


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSlaverProxy = SlaverProxy.getInstance();

        IntentFilter filter = new IntentFilter(PeripheralService.ACTION_WRITE_REQUEST);
        filter.addAction(PeripheralService.ACTION_CONNECTED);
        filter.addAction(PeripheralService.ACTION_DISCONNECTED);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mGattReceiver, filter);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_slaver, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        /*AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
        AdvertiseSettings.ADVERTISE_TX_POWER_LOW
        AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
        AdvertiseSettings.ADVERTISE_TX_POWER_HIGH*/
        String[] txPowerArr = new String[]{"Ultra Low", "Low", "Medium", "High"};
        ArrayAdapter<String> txPowerAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, txPowerArr);
        mTxPowerSpinner = (Spinner) view.findViewById(R.id.spinner_tx_power);
        mTxPowerSpinner.setAdapter(txPowerAdapter);
        mTxPowerSpinner.setSelection(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);

        /*AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        AdvertiseSettings.ADVERTISE_MODE_BALANCED
        AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY*/
        String[] advModeArr = new String[]{"Low Power", "Balanced", "Low Latency"};
        ArrayAdapter<String> advModeAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, advModeArr);
        mAdvModeSpinner = (Spinner) view.findViewById(R.id.spinner_adv_mode);
        mAdvModeSpinner.setAdapter(advModeAdapter);
        mAdvModeSpinner.setSelection(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);

        mEdtLocalName = (EditText) view.findViewById(R.id.edt_adv_name);
        mEdtMfrData = (EditText) view.findViewById(R.id.edt_adv_mfr_data);
        mEdtLocalName.setText(Build.MODEL);//默认广播名称为手机型号
        mEdtTxData = (EditText) view.findViewById(R.id.edt_tx_data);

        mRxDataListAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1);
        mRxDataListAdapter.setNotifyOnChange(true);
        mRxDataList = (ListView) view.findViewById(R.id.slaver_rx_data_list);
        mRxDataList.setAdapter(mRxDataListAdapter);

        mConnectionState = (TextView) view.findViewById(R.id.slaver_connection_state);

        // isMultipleAdvertisementSupported()给的结果不准
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean advUnsupported = btAdapter.isEnabled() && !btAdapter.isMultipleAdvertisementSupported();
        view.findViewById(R.id.advertising_not_supported).setVisibility(advUnsupported ? View.VISIBLE : View.GONE);

        view.findViewById(R.id.btn_notify_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //todo 发送数据
                byte[] data = inputToByteArray(mEdtTxData.getText().toString().trim());
                mSlaverProxy.notifySend(data);
            }
        });
        ((ToggleButton) view.findViewById(R.id.toggleButton)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mSlaverProxy.startAdvertising(
                            mEdtLocalName.getText().toString().trim(),//广播名称
                            mAdvModeSpinner.getSelectedItemPosition(),//广播模式
                            mTxPowerSpinner.getSelectedItemPosition(),//发射功率
                            inputToByteArray(mEdtMfrData.getText().toString().trim())//厂商数据
                    );
                } else {
                    mSlaverProxy.stopAdvertising();
                }
            }
        });
    }


    private byte[] inputToByteArray(String s) {
        if (s == null) return null;

        if (s.trim().length() > 0) {
            byte[] v;
            try {
                v = Util.hexToByteArray(s);
            } catch (Exception e) {
                v = s.getBytes();
            }
            return v;
        }
        return null;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mGattReceiver);
    }
}