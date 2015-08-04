package com.xiaomi;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by rickwang on 2015/06/15.
 */

public class MiBand extends BluetoothGattCallback {
    static final String T = "MiBand";

    static final UUID UUID_SERVICE = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb");
    static final UUID UUID_PAIR = UUID.fromString("0000ff0f-0000-1000-8000-00805f9b34fb");
    static final UUID UUID_USER_INFO = UUID.fromString("0000ff04-0000-1000-8000-00805f9b34fb");
    static final UUID UUID_CONTROL_POINT = UUID.fromString("0000ff05-0000-1000-8000-00805f9b34fb");
    static final UUID UUID_REALTIME_STEPS = UUID.fromString("0000ff06-0000-1000-8000-00805f9b34fb");

    static final long TIMEOUT = 5000L;

    final Context context;
    final String id;
    final BluetoothDevice device;

    Callback callback = new Callback();

    BluetoothGatt gatt;

    BlockingQueue<Task> tasks = new ArrayBlockingQueue<>(20);
    Task task;

    ExecutorService executor = Executors.newSingleThreadExecutor();

    public MiBand(Context context, BluetoothDevice device) {
        this.context = context;
        this.id = device.getAddress();
        this.device = device;
    }

    public String getId() {
        return id;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    // ======

    public void connect() {
        gatt = device.connectGatt(context, false, this);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                process();
            }
        });
    }

    public void close() {
        gatt.close();

        ExecutorService es = executor;
        executor = null;
        es.shutdown();
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.i(T, String.format("onConnectionStateChange - from %d to %d", status, newState));
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices();

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.e(T, device.getAddress() + " is disconnected.");

            callback.onDisconnected(this);
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.i(T, "onServicesDiscovered - " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            callback.onReady(this);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        byte[] value = characteristic.getValue();
        Log.i(T, "onCharacteristicRead - " + characteristic.getUuid() + " : " + toString(value));
        task.setResponse(value);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        byte[] value = characteristic.getValue();
        Log.i(T, "onCharacteristicWrite - " + characteristic.getUuid() + " : " + toString(value));
        task.setResponse(value);
    }

    protected String toString(byte[] bytes) {
        if (bytes == null) return "";

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    // ======

    protected byte[] execute(Task task, long timeout) throws InterruptedException {
        tasks.put(task);
        return task.getResponse(timeout);
    }

    protected void put(Task task) {
        try {
            tasks.put(task);

        } catch (Exception ex) {
            Log.e(T, "Failed to put the task", ex);
        }
    }

    protected void process() {
        while (executor != null) {
            try {
                task = tasks.take();
                task.execute();
                task.getResponse(TIMEOUT);

            } catch (Exception ex) {
                Log.e(T, "Failed to handle task", ex);
            }
        }
    }

    // ======

//    protected void pair(final BluetoothGatt gatt) {
//        put(new Task() {
//            @Override
//            public void execute() {
//                BluetoothGattService srv = gatt.getService(UUID_SERVICE);
//                BluetoothGattCharacteristic bgc = srv.getCharacteristic(UUID_PAIR);
//                bgc.setValue(new byte[] { 2 });
//                gatt.writeCharacteristic(bgc);
//            }
//        });
//    }

    protected int crc8(byte[] bytes, int len) {
        int crc = 0;
        for (int a = 0; a < len;a++) {
            byte b = bytes[a];
            crc = crc ^ (b & 0xff);
            for (int i = 0;i < 8;i++) {
                if ((crc & 0x01) != 0) {
                    crc = (crc >> 1) ^ 0x8c;
                } else {
                    crc = crc >> 1;
                }
            }
        }

        return crc;
    }

    public void setUserInfo() throws InterruptedException {
        Task task = new Task() {
            @Override
            public void execute() {
                byte[] bytes = new byte[20];

                String alias = "1550050550";
                byte gender = 1;
                byte age = 39;
                byte height = (byte) 179;
                byte weight = 74;
                byte type = 0;

                int uid = Integer.parseInt(alias);
                bytes[0] = (byte) uid;
                bytes[1] = (byte) (uid >>> 8);
                bytes[2] = (byte) (uid >>> 16);
                bytes[3] = (byte) (uid >>> 24);
                bytes[4] = gender;
                bytes[5] = age;
                bytes[6] = height;
                bytes[7] = weight;
                bytes[8] = type;

                byte[] as = alias.getBytes();
                for (int i = 9; i < 19; i++) {
                    bytes[i] = as[i - 9];
                }

                String address = device.getAddress();
                address = address.substring(address.length() - 2);

                int a = crc8(bytes, 19) ^ Integer.decode("0x" + address).intValue();
                bytes[19] = (byte) a;

                BluetoothGattService srv = gatt.getService(UUID_SERVICE);
                BluetoothGattCharacteristic bgc = srv.getCharacteristic(UUID_USER_INFO);
                bgc.setValue(bytes);
                gatt.writeCharacteristic(bgc);
            }
        };

        put(task);

        task.getResponse(TIMEOUT);
    }

    public void vibrate() throws InterruptedException {
        Task task = new Task() {
            @Override
            public void execute() {
                Log.i(T, "vibrate");

                BluetoothGattService srv = gatt.getService(UUID_SERVICE);
                BluetoothGattCharacteristic bgc = srv.getCharacteristic(UUID_CONTROL_POINT);
                bgc.setValue(new byte[]{8, 0}); // Vibrate and flash LEDs to locate your miband.
                gatt.writeCharacteristic(bgc);
            }
        };

        put(task);

        task.getResponse(TIMEOUT);
    }

    public int getSteps() throws InterruptedException {
        Task t = new Task() {
            @Override
            public void execute() {
                BluetoothGattService srv = gatt.getService(UUID_SERVICE);
                BluetoothGattCharacteristic bgc = srv.getCharacteristic(UUID_REALTIME_STEPS);
                gatt.readCharacteristic(bgc);
            }
        };

        put(t);

        int steps = 0;

        byte[] bytes = t.getResponse(TIMEOUT);
        if (bytes != null) {
            steps = (bytes[0] & 0x0FF) | ((bytes[1] & 0x0FF) << 8) | ((bytes[2] & 0x0FF) << 16) | ((bytes[3] & 0x0FF) << 24);
        }

        return steps;
    }

//    public void refactory() {
//        put(new Task() {
//            @Override
//            public void execute() {
//                BluetoothGattService srv = gatt.getService(UUID_SERVICE);
//                BluetoothGattCharacteristic bgc = srv.getCharacteristic(UUID_CONTROL_POINT);
//                bgc.setValue(new byte[] { 9 });
//                gatt.writeCharacteristic(bgc);
//            }
//        });
//    }

    // ======

    static abstract class Task {
        byte[] response;

        public abstract void execute();

        public synchronized void setResponse(byte[] response) {
            this.response = response;
            notifyAll();
        }

        public synchronized byte[] getResponse(long timeout) throws InterruptedException {
            if (response == null) {
                wait(timeout);
            }

            return response;
        }
    }

    public static class Callback {
        protected void onReady(MiBand mi) {
        }

        protected void onDisconnected(MiBand mi) {
        }
    }
}