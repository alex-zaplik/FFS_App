/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothchat;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.example.android.common.logger.Log;
import com.example.android.ffs.FFSPeggy;
import com.example.android.ffs.FFSVictor;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothChatFragment extends Fragment {

    private static final String TAG = "BluetoothChatFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private ListView mConversationView;
    private Button mSendButton;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;

    /**
     * FFS data
     */
    private boolean isPeggy = false;
    private FFSPeggy peggy;
    private FFSVictor victor;

    private BigInteger ffsN;
    private ArrayList<BigInteger> ffsS = new ArrayList<>();

    private ArrayList<BigInteger> ffsNV;
    private BigInteger ffsX;
    private ArrayList<Boolean> ffsA;
    private BigInteger ffsY;
    private boolean ffsC;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        FragmentActivity activity = getActivity();
        if (mBluetoothAdapter == null && activity != null) {
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mBluetoothAdapter == null) {
            return;
        }
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mConversationView = view.findViewById(R.id.in);
        mSendButton = view.findViewById(R.id.button_send);
    }

    private void initFFS(int l, int k) {
        SecureRandom random = new SecureRandom();

//        BigInteger n = new BigInteger("3501123401"); // 56467 * 62003
//        mConversationArrayAdapter.add("Me:  N = " + n);

        if (ffsS.isEmpty()) {
            BigInteger p = BigInteger.probablePrime(l, random);
            BigInteger q = BigInteger.probablePrime(l, random);
            ffsN = p.multiply(q);

//            mConversationArrayAdapter.add("FFS:  " + p);
//            mConversationArrayAdapter.add("FFS:  " + q);
//            mConversationArrayAdapter.add("FFS:  " + n);

            while (ffsS.size() < k)
            {
                BigInteger bi = new BigInteger(2 * l, random);
                if (bi.gcd(ffsN).equals(new BigInteger("1"))) {
                    ffsS.add(bi);
                }
            }
        }

        byte[] peggySeed = SecureRandom.getSeed(l);
        byte[] victorSeed = SecureRandom.getSeed(l);

        peggy = new FFSPeggy(ffsN, ffsS, l, k, peggySeed);
        victor = new FFSVictor(ffsN, k, victorSeed);
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }
        mConversationArrayAdapter = new ArrayAdapter<>(activity, R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the send button with a listener that for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
//                    TextView textView = view.findViewById(R.id.edit_text_out);
//                    String message = textView.getText().toString();
//                    sendMessage(message);

                    if (isPeggy) {
                        switch (peggy.getCurrentStep()) {
                            case 0: {
                                ffsNV = peggy.getV();
                                sendBigIntegerArray(ffsNV);
                                break;
                            }
                            case 1: {
                                ffsX = peggy.getX();
                                sendBigIntegerArray(new ArrayList<>(Collections.singletonList(ffsX)));
                                break;
                            }
                            case 2: {
                                if (victor.getCurrentStep() == 2) {
                                    ffsY = peggy.getY(ffsA);
                                    sendBigIntegerArray(new ArrayList<>(Collections.singletonList(ffsY)));
                                } else {
                                    mConversationArrayAdapter.add("Me: Waiting for Victor...");
                                }
                                break;
                            }
                        }
                    } else {
                        switch (victor.getCurrentStep()) {
                            case 0: {
                                if (peggy.getCurrentStep() == 2) {
                                    victor.receiveV(ffsNV);
                                    ffsA = victor.getA(ffsX);
                                    sendBooleanArray(ffsA);
                                } else {
                                    mConversationArrayAdapter.add("Me: Waiting for Peggy...");
                                }
                                break;
                            }
                            case 1: {
                                break;
                            }
                            case 2: {
                                if (peggy.getCurrentStep() == 3) {
                                    ffsC = victor.check(ffsY);
                                    sendBooleanArray(new ArrayList<>(Collections.singletonList(ffsC)));

                                    victor.setCurrentStep(0);
                                    peggy.setCurrentStep(1);
                                } else {
                                    mConversationArrayAdapter.add("Me: Waiting for Peggy...");
                                }
                                break;
                            }
                        }
                    }
                }
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(activity, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer();
    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] data = message.getBytes();

            byte[] send = new byte[data.length + 1];
            System.arraycopy(data, 0, send, 1, data.length);
            mChatService.write(send);
        }
    }

    static ArrayList<BigInteger> toBigIntegerArray(byte[] bytes) {
        ArrayList<BigInteger> data = new ArrayList<>();

        for (int i = 1; i + 1 < bytes.length; ) {
            int length = bytes[i] * 128 + bytes[i + 1];
            byte[] intBytes = new byte[length];

            i += 2;

            System.arraycopy(bytes, i, intBytes, 0, length);
            data.add(new BigInteger(intBytes));

            i += length;
        }

        return data;
    }

    static byte[] toByteArray(ArrayList<BigInteger> ints) {
        ArrayList<byte[]> data = new ArrayList<>();
        int length = 1;

        for (BigInteger bigInteger : ints) {
            byte[] send = bigInteger.toByteArray();
            data.add(send);
            length += send.length + 2;
        }

        byte[] bytes = new byte[length];
        bytes[0] = 0b00000001;
        int pos = 1;

        for (byte[] d : data) {
            bytes[pos] = (byte)(d.length / 128);
            bytes[pos + 1] = (byte)(d.length % 128);
            pos += 2;

            System.arraycopy(d, 0, bytes, pos, d.length);
            pos += d.length;
        }

        return bytes;
    }

    private void sendBigIntegerArray(ArrayList<BigInteger> arr) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (arr.size() > 0) {
            // Send int array
            byte[] send = toByteArray(arr);
            mChatService.write(send);
        }
    }

    static ArrayList<Boolean> toBooleanArray(byte[] bytes) {
        BitSet bits = BitSet.valueOf(bytes);
        ArrayList<Boolean> bools = new ArrayList<>();
        for (int i = 0; i < bytes.length * 8; i++) {
            bools.add(false);
        }
        for (int i = bits.nextSetBit(0); i != -1; i = bits.nextSetBit(i+1)) {
            bools.set(i, true);
        }
        return bools;
    }

    static byte[] toByteArray(Boolean[] bools) {
        BitSet bits = new BitSet(bools.length);
        for (int i = 0; i < bools.length; i++) {
            if (bools[i]) {
                bits.set(i);
            }
        }

        byte[] bytes = bits.toByteArray();
        if (bytes.length * 8 >= bools.length) {
            return bytes;
        } else {
            return Arrays.copyOf(bytes, bools.length / 8 + (bools.length % 8 == 0 ? 0 : 1));
        }
    }

    private void sendBooleanArray(ArrayList<Boolean> arr) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (arr.size() > 0) {
            // Send boolean array
            Boolean[] boolsArr = new Boolean[arr.size()];
            boolsArr = arr.toArray(boolsArr);

            byte[] data = toByteArray(boolsArr);

            byte[] send = new byte[data.length + 1];
            send[0] = 0b00000010;
            System.arraycopy(data, 0, send, 1, data.length);

            mChatService.write(send);
        }
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        private void handleData(String prefix, byte[] arr) {
            byte type = arr[0];
            StringBuilder message = new StringBuilder();

            switch (type) {
                case 0b00000000: {
                    // String
                    message = new StringBuilder(new String(Arrays.copyOfRange(arr, 1, arr.length)));
                    break;
                }
                case 0b00000001: {
                    // BigInteger
                    ArrayList<BigInteger> data = toBigIntegerArray(arr);
                    message = new StringBuilder();
                    boolean close = false;
                    int start = 0;

                    if (isPeggy) {
                        if (peggy.getCurrentStep() == 1) {
                            message.append("N = ").append(data.get(0)).append("; V = [");
                            data.remove(0);
                            close = true;
                        } else if (peggy.getCurrentStep() == 2) {
                            message.append("X = ");
                        } else if (peggy.getCurrentStep() == 3) {
                            message.append("Y = ");
                        }
                    } else {
                        if (peggy.getCurrentStep() == 0) {
                            peggy.nextStep();
                            message.append("N = ").append(data.get(0)).append("; V = [");
                            ffsNV = data;
                            close = true;
                            start = 1;
                        } else if (peggy.getCurrentStep() == 1) {
                            peggy.nextStep();
                            ffsX = data.get(0);
                            message.append("X = ");
                        } else if (peggy.getCurrentStep() == 2) {
                            peggy.nextStep();
                            ffsY = data.get(0);
                            message.append("Y = ");
                        }
                    }

                    for (int i = start; i < data.size(); i++) {
                        message.append(data.get(i));

                        if (i < data.size() - 1) {
                            message.append(" ");
                        }
                    }

                    if (close) message.append("]");

                    break;
                }
                case 0b00000010: {
                    // Boolean
                    ArrayList<Boolean> data = toBooleanArray(Arrays.copyOfRange(arr, 1, arr.length));
                    message = new StringBuilder();
                    boolean close = false;
                    int size = 1;

                    if (isPeggy) {
                        if (victor.getCurrentStep() == 0) {
                            victor.nextStep();
                            victor.nextStep();
                            ffsA = data;
                            message.append("A = [");
                            close = true;
                            size = data.size();
                        } else if (victor.getCurrentStep() == 2) {
                            victor.nextStep();
                            ffsC = data.get(0);
                            message.append("Verified = ");
                            victor.setCurrentStep(0);
                            peggy.setCurrentStep(1);
                        }
                    } else {
                        if (victor.getCurrentStep() == 2) {
                            message.append("A = [");
                            close = true;
                            size = data.size();
                        } else {
                            message.append("Verified = ");
                        }
                    }

                    for (int i = 0; i < size; i++) {
                        message.append(data.get(i));

                        if (i < size - 1) {
                            message.append(" ");
                        }
                    }

                    if (close) message.append("]");

                    break;
                }
            }

            mConversationArrayAdapter.add(prefix + message.toString());
        }

        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            initFFS(16, 8);
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            isPeggy = false;
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    handleData("Me: ", writeBuf);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    handleData(mConnectedDeviceName + ":  ", Arrays.copyOfRange(readBuf, 0, msg.arg1));
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        Toast.makeText(activity, R.string.bt_not_enabled_leaving,
                                Toast.LENGTH_SHORT).show();
                        activity.finish();
                    }
                }
        }
    }

    /**
     * Establish connection with other device
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        Bundle extras = data.getExtras();
        if (extras == null) {
            return;
        }
        String address = extras.getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);

                isPeggy = true;
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);

                isPeggy = true;
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }

}
