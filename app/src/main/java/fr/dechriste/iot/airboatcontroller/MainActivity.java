package fr.dechriste.iot.airboatcontroller;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.internal.RxBleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.github.controlwear.virtual.joystick.android.JoystickView;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {
    private static final int DELAY_BLE_MESSAGE_MS = 60;
    private static final UUID RX_TX_CHARACTERISTIC = UUID.fromString(BleUtils.RX_TX_CHARACTERISTIC);

    private Handler mHandler = new Handler();
    private BluetoothUpdateRunnable mRunnable = new BluetoothUpdateRunnable();
    private boolean mTrimModeEnabled;

    @BindView(R.id.ic_connection_state)
    ImageView vConnectionState;

    @BindView(R.id.deviceMacAddress)
    TextView vDeviceMacAddress;

    @BindView(R.id.selectBoatButton)
    Button vSelectButton;

    @BindView(R.id.connectButton)
    Button vConnectButton;

    @BindView(R.id.disconnectButton)
    Button vDisconnectButton;

    @BindView(R.id.progressBar)
    ProgressBar vProgressBar;

    @BindView(R.id.rightJoystickView)
    JoystickView vRightJoystick;

    @BindView(R.id.leftJoystickView)
    JoystickView vLeftJoystick;

    @BindView(R.id.trimLeft)
    Button vTrimLeft;

    @BindView(R.id.trimRight)
    Button vTrimRight;

    @BindView(R.id.trimReset)
    Button vTrimReset;

    private class BluetoothUpdateRunnable implements Runnable {
        @Override
        public void run() {
            if (isConnected() && !mTrimModeEnabled) {
                String message = String.format(Locale.ENGLISH, "AT$PARAMS:%d;%d;%d", mDirection, mYPercent, mXPercent);
                sendBleMessage(message);
            }
            mHandler.postDelayed(mRunnable, DELAY_BLE_MESSAGE_MS);
        }
    }

    private JoystickView.OnMoveListener mOnRightJoystickMoveListener = (angle, strength) -> {
        mXPercent = (angle == 180) ? -strength / 2 : strength / 2;
        Timber.d("Right Joystick: angle %d and strength %d", angle, strength);
    };

    private JoystickView.OnMoveListener mOnLeftJoystickMoveListener = (angle, strength) -> {
        Timber.d("Left Joystick: angle %d and strength %d", angle, strength);
        mYPercent = strength;
        if(strength == 0) {
            mDirection = 0;
        } else {
            mDirection = (angle == 270) ? -1 : 1;
        }
    };

    private RxBleClient mRxBleClient;
    private RxBleDevice mBleDevice;
    private RxBleConnection mRxBleConnection;
    private Disposable mBleConnectionDisposable;

    private volatile int mYPercent = 0;
    private volatile int mXPercent = 50;
    private volatile int mDirection = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        vProgressBar.setVisibility(View.GONE);
        vSelectButton.setEnabled(true);
        vConnectButton.setEnabled(false);
        vDisconnectButton.setEnabled(false);
        vRightJoystick.setEnabled(false);
        vLeftJoystick.setEnabled(false);
        vTrimLeft.setVisibility(View.GONE);
        vTrimRight.setVisibility(View.GONE);
        vTrimReset.setVisibility(View.GONE);

        vRightJoystick.setOnMoveListener(mOnRightJoystickMoveListener);
        vLeftJoystick.setOnMoveListener(mOnLeftJoystickMoveListener);

        mRxBleClient = BleUtils.getClientInstance(getApplicationContext());

        mHandler.post(mRunnable);

        RxBleClient.setLogLevel(RxBleLog.VERBOSE);
        RxBleLog.setLogger((level, tag, msg) -> Timber.tag(tag).log(level, msg));
    }

    @Override
    protected void onDestroy() {
        if(mBleConnectionDisposable != null && mBleConnectionDisposable.isDisposed()) {
            mBleConnectionDisposable.dispose();
        }
        mHandler.removeCallbacks(mRunnable);

        super.onDestroy();
    }

    @OnClick(R.id.selectBoatButton)
    public void onSelectButtonClick() {
        startActivityForResult(new Intent(this, ScanActivity.class), ScanActivity.SELECT_BOAT_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == ScanActivity.SELECT_BOAT_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String macAddress = data.getAction();
            if(macAddress != null) {
                vDeviceMacAddress.setText(macAddress);
                mBleDevice = mRxBleClient.getBleDevice(macAddress);
                vConnectButton.setEnabled(true);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @OnClick(R.id.connectButton)
    void connect() {
        vProgressBar.setVisibility(View.VISIBLE);
        vSelectButton.setEnabled(false);
        vConnectButton.setEnabled(false);

        mBleConnectionDisposable = mBleDevice.establishConnection(false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    rxBleConnection -> {
                        Timber.i("Connection has been established!");
                        vDisconnectButton.setEnabled(true);
                        vProgressBar.setVisibility(View.GONE);
                        vConnectionState.setImageResource(R.drawable.ic_bluetooth_connected_green);
                        vRightJoystick.setEnabled(true);
                        vLeftJoystick.setEnabled(true);

                        // All GATT operations are done through the rxBleConnection.
                        mRxBleConnection = rxBleConnection;
                    },
                    throwable -> {
                        Timber.e(throwable, "Connection failed");
                        Snackbar.make(findViewById(R.id.main_layout), "Connection failed", Snackbar.LENGTH_SHORT).show();
                        vSelectButton.setEnabled(true);
                        vConnectButton.setEnabled(true);
                        vRightJoystick.setEnabled(false);
                        vLeftJoystick.setEnabled(false);
                        vProgressBar.setVisibility(View.GONE);
                        vConnectionState.setImageResource(R.drawable.ic_bluetooth_red);
                    }
                );
    }

    @OnClick(R.id.disconnectButton)
    void disconnect() {
        vSelectButton.setEnabled(true);
        vConnectButton.setEnabled(true);
        vDisconnectButton.setEnabled(false);
        vRightJoystick.setEnabled(false);
        vLeftJoystick.setEnabled(false);
        vConnectionState.setImageResource(R.drawable.ic_bluetooth_red);
        if(mBleConnectionDisposable != null && !mBleConnectionDisposable.isDisposed()) {
            mBleConnectionDisposable.dispose();
            mBleConnectionDisposable = null;
            mRxBleConnection = null;
        }
    }

    private boolean isConnected() {
        return mBleDevice != null && mBleDevice.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private void sendBleMessage(String message) {
        if(mRxBleConnection != null && isConnected()) {
            Disposable disposable = mRxBleConnection.writeCharacteristic(RX_TX_CHARACTERISTIC, message.getBytes())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                    characteristicValue -> {
                        // Characteristic value confirmed.
                    },
                    throwable -> {
                        Timber.e(throwable, "Write failed");
                        Snackbar.make(findViewById(R.id.main_layout), "Unable to send the direction to the device", Snackbar.LENGTH_SHORT).show();
                    }
                );
        }
    }

    @OnClick(R.id.trimMode)
    void onTrimButtonClick(Switch buttonView) {
        mTrimModeEnabled = buttonView.isChecked();

        vTrimLeft.setVisibility(mTrimModeEnabled ? View.VISIBLE : View.GONE);
        vTrimRight.setVisibility(mTrimModeEnabled ? View.VISIBLE : View.GONE);
        vTrimReset.setVisibility(mTrimModeEnabled ? View.VISIBLE : View.GONE);

        vLeftJoystick.setVisibility(mTrimModeEnabled ? View.GONE : View.VISIBLE);
        vRightJoystick.setVisibility(mTrimModeEnabled ? View.GONE : View.VISIBLE);
    }

    @OnClick(R.id.trimRight)
    void onTrimRightButtonClick(Button buttonView) {
        sendBleMessage("AT$TRIM:+");
    }

    @OnClick(R.id.trimLeft)
    void onTrimLeftButtonClick(Button buttonView) {
        sendBleMessage("AT$TRIM:-");
    }

    @OnClick(R.id.trimReset)
    void onTrimResetButtonClick(Button buttonView) {
        sendBleMessage("AT$TRIM:0");
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // Check that the event came from a game controller
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            // Process all historical movement samples in the batch
            final int historySize = event.getHistorySize();

            // Process the movements starting from the
            // earliest historical position in the batch
            for (int i = 0; i < historySize; i++) {
                // Process the event at historical position i
                processJoystickInput(event, i);
            }

            // Process the current movement sample in the batch (position -1)
            processJoystickInput(event, -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean handled = false;
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            if (event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (event.getKeyCode()) {
                    case 4: // escape
                        mDirection = 0;
                        mYPercent = 0;
                        handled = true;
                        break;
                    case 108: // start
                        if(mBleDevice != null) {
                            if (!isConnected()) {
                                connect();
                            } else {
                                disconnect();
                            }
                        }
                        handled = true;
                        break;
                    case 100: // Y
                        if(mTrimModeEnabled) {
                            sendBleMessage("AT$TRIM:+");
                        } else {
                            mYPercent = Math.min(mYPercent + 10, 100);
                            if(mDirection == 0 && mYPercent > 0) {
                                mDirection = 1;
                            }
                        }
                        handled = true;
                        break;
                    case 99: // X
                        if(mTrimModeEnabled) {
                            sendBleMessage("AT$TRIM:-");
                        } else {
                            mYPercent = Math.max(mYPercent - 10, 0);
                        }
                        handled = true;
                        break;
                    case 96: // A
                        mYPercent = 10;
                        if(mDirection == 0 && mYPercent > 0) {
                            mDirection = 1;
                        }
                        handled = true;
                        break;
                    case 97: // B
                        mYPercent = 100;
                        if(mDirection == 0 && mYPercent > 0) {
                            mDirection = 1;
                        }
                        handled = true;
                        break;
                    case 109: // Select
                        handled = true;
                        mDirection = mDirection == -1 ? 1 : -1;
                        break;
                    // Handle gamepad
                    default:
                        Timber.d("Key pressed: %d", event.getKeyCode());
                        handled = true;
                }
            }
            if (handled) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void processJoystickInput(MotionEvent event, int historyPos) {
        InputDevice mInputDevice = event.getDevice();

        // Calculate the horizontal distance to move by
        // using the input value from one of these physical controls:
        // the left control stick, hat axis, or the right control stick.
        float x = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_X, historyPos);
        if (x == 0) {
            x = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_HAT_X, historyPos);
        }
        if (x == 0) {
            x = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_Z, historyPos);
        }

        // Calculate the vertical distance to move by
        // using the input value from one of these physical controls:
        // the left control stick, hat switch, or the right control stick.
        float y = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_Y, historyPos);
        if (y == 0) {
            y = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_HAT_Y, historyPos);
        }
        if (y == 0) {
            y = getCenteredAxis(event, mInputDevice, MotionEvent.AXIS_RZ, historyPos);
        }

        // Update the ship object based on the new x and y values
        Timber.d("Joystick X: %f", x);
        Timber.d("Joystick Y: %f", y);

        mXPercent = (int)((x + 1) / 2f * 100);
        //mYPercent = (int)(Math.abs(y) * 100);
        if(y > 0.1) {
            mDirection = -1;
        } else if(y < -0.1) {
            mDirection = 1;
        } else if(mYPercent == 0) {
            mDirection = 0;
        }
    }

    public List getGameControllerIds() {
        List<Integer> gameControllerDeviceIds = new ArrayList<>();
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice dev = InputDevice.getDevice(deviceId);
            int sources = dev.getSources();

            // Verify that the device has gamepad buttons, control sticks, or both.
            if (((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                    || ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                // This device is a game controller. Store its device ID.
                if (!gameControllerDeviceIds.contains(deviceId)) {
                    gameControllerDeviceIds.add(deviceId);
                }
            }
        }
        return gameControllerDeviceIds;
    }

    private static float getCenteredAxis(MotionEvent event, InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            final float value = historyPos < 0 ? event.getAxisValue(axis) : event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }
}
