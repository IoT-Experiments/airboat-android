package fr.dechriste.iot.airboatcontroller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

public class ScanActivity extends AppCompatActivity {
    public static final int SELECT_BOAT_REQUEST_CODE = 0x123;

    private ArrayList<RxBleDevice> mDevices = new ArrayList<>();

    private RxBleClient mRxBleClient;
    private Disposable mScanDisposable;

    @BindView(R.id.scanned_devices)
    RecyclerView vScannedDevices;

    class BleDeviceViewHolder extends RecyclerView.ViewHolder {
        final TextView mDeviceName;
        final TextView mDeviceMacAddress;
        RxBleDevice mDevice;

        BleDeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            mDeviceName = itemView.findViewById(R.id.deviceName);
            mDeviceMacAddress = itemView.findViewById(R.id.deviceMacAddress);
        }
    }

    private RecyclerView.Adapter mAdapter = new RecyclerView.Adapter<BleDeviceViewHolder>() {
        @NonNull
        @Override
        public BleDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new BleDeviceViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.ble_device, viewGroup, false));
        }

        @Override
        public void onBindViewHolder(@NonNull BleDeviceViewHolder bleDeviceViewHolder, int i) {
            bleDeviceViewHolder.mDevice = mDevices.get(i);
            String deviceName = bleDeviceViewHolder.mDevice.getName();
            if(deviceName == null) {
                deviceName = "Unknown device";
            }
            bleDeviceViewHolder.mDeviceName.setText(deviceName);
            bleDeviceViewHolder.mDeviceMacAddress.setText(bleDeviceViewHolder.mDevice.getMacAddress());
            bleDeviceViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setResult(RESULT_OK, new Intent(bleDeviceViewHolder.mDevice.getMacAddress()));
                    finish();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mDevices.size();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scan);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ButterKnife.bind(this);

        vScannedDevices.setLayoutManager(new LinearLayoutManager(this));
        vScannedDevices.setHasFixedSize(false);
        vScannedDevices.setAdapter(mAdapter);

        mRxBleClient = BleUtils.getClientInstance(getApplicationContext());
    }

    private void askForPermissions() {
        PermissionListener dialogPermissionListener =
                DialogOnDeniedPermissionListener.Builder
                        .withContext(this)
                        .withTitle("Access location permission")
                        .withMessage("Access location permission is mandatory to list bluetooth devices.")
                        .withButtonText(android.R.string.ok)
                        .build();

        Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(dialogPermissionListener)
                .check();
    }

    @Override
    protected void onResume() {
        super.onResume();

        askForPermissions();

        checkBluetoothEnabled();

        mScanDisposable = mRxBleClient.scanBleDevices(
                    new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build(),
                    new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleUtils.RX_TX_CHARACTERISTIC), ParcelUuid.fromString(BleUtils.SERVICE_UUID_MASK)).build())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                rxBleScanResult -> {
                    if (!mDevices.contains(rxBleScanResult.getBleDevice())) {
                        mDevices.add(rxBleScanResult.getBleDevice());
                        mAdapter.notifyDataSetChanged();
                    }
                },
                throwable -> {
                    Snackbar.make(findViewById(R.id.main_layout), "Scan failed", Snackbar.LENGTH_SHORT).show();
                    Timber.e(throwable);
                });
    }

    private void checkBluetoothEnabled() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            new MaterialDialog.Builder(this)
                .title("Bluetooth not available")
                .content("Your device is not compatible with this app requirements")
                .show();
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                // Bluetooth is not enable :)
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
            }
        }
    }

    @Override
    protected void onPause() {
        if(mScanDisposable != null && !mScanDisposable.isDisposed()) {
            mScanDisposable.dispose();
        }
        super.onPause();
    }
}
