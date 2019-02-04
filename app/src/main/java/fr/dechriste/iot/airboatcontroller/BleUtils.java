package fr.dechriste.iot.airboatcontroller;

import android.content.Context;

import com.polidea.rxandroidble2.RxBleClient;

import androidx.annotation.NonNull;

public class BleUtils {
    public static final String RX_TX_CHARACTERISTIC = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static final String SERVICE_UUID_MASK = "fffffff0-ffff-ffff-ffff-ffffffffffff";

    private static RxBleClient mRxBleClient;

    public static RxBleClient getClientInstance(@NonNull Context context) {
        if(mRxBleClient == null) {
            mRxBleClient = RxBleClient.create(context);
        }
        return mRxBleClient;
    }
}
