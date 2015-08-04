package ntw.idea.org.ntw;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.xiaomi.MiBand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by rickwang on 2015/06/23.
 */
public class MainService extends Service {
    static final String T = "service";

    static final long CHECK_INTERVAL = 600_000L;
    static final long ONE_DAY = 24 * 3600_000L;

    final IBinder binder = new MyBinder();

    Handler handler;

    ResultListener listener = new NullResultListener();

    MiBand.Callback callback = new MiBandCallback();
    BroadcastReceiver receiver;
    Map<String, MiBandBag> bags = Collections.synchronizedMap(new HashMap<String, MiBandBag>()); // mac -> MiBandBag
    ExecutorService executor = Executors.newSingleThreadExecutor();

    List<WalkResult> results = Collections.synchronizedList(new ArrayList<WalkResult>());

//    String me = "88:0F:10:20:7F:CF";
    long interval = 3600_000L;
    int expect = 60;

//    long interval = 10_000L;
//    int expect = 40;

    List<BluetoothDevice> devices = Collections.synchronizedList(new ArrayList<BluetoothDevice>());

    public MainService() {
    }

    public void setResultListener(ResultListener listener) {
        this.listener = listener;
    }

    public void unsetResultListener() {
        this.listener = new NullResultListener();
    }

    // ======

    @Override
    public void onCreate() {
        super.onCreate();

        handler = new Handler();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    listener.message(System.currentTimeMillis(), "[%s] %s", device.getAddress(), device.getName());

                    devices.add(device);

                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    onDiscoveryFinished();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter);

        handler.post(new Runnable() {
            @Override
            public void run() {
                process();
            }
        });

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                process();

                try {
                    Log.i(T, "Register the next round");

                    handler.postDelayed(this, CHECK_INTERVAL);

                } catch (Exception e) {
                    Log.e(T, e.getMessage(), e);
                }
            }
        }, CHECK_INTERVAL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        executor.shutdown();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ======

    public List<WalkResult> getWalkResults() {
        return results;
    }

    protected void saveWalkResult(WalkResult result) {
        results.add(result);

        long now = result.getEnd();

        synchronized (results) {
            Iterator<WalkResult> it = results.iterator();
            while (it.hasNext()) {
                result = it.next();
                if ((now - result.getStart()) > ONE_DAY) {
                    it.remove();
                }
            }
        }
    }

    // ======

    protected void onFound(BluetoothDevice device) {
        String name = device.getName();
        if ("MI".equals(name)) { // Mi Bracelet
//            if (address.equals(me)) {
                MiBand mi = new MiBand(MainService.this, device);
                mi.setCallback(callback);
                mi.connect();
//            }
        }
    }

    protected void onDiscoveryFinished() {
        listener.message(System.currentTimeMillis(), "Discovery is finished");

        synchronized (devices) {
            for (BluetoothDevice device : devices) {
                onFound(device);
            }
        }
    }

    public void process() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                listener.onProcess();

                devices.clear();

                BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
                ba.startDiscovery(); // 12 seconds

                listener.message(System.currentTimeMillis(), "Discovery is starting.");
            }
        });
    }

    protected MiBandBag getBag(String mac) {
        MiBandBag bag = bags.get(mac);
        if (bag == null) {
            bag = new MiBandBag(mac);
            bags.put(mac, bag);
        }

        return bag;
    }

    protected boolean check(long now, String id, int steps) {
        boolean underflow = false;

        MiBandBag bag = getBag(id);
        long last = bag.getLast();
        long elapse = now - last;
        int s = steps - bag.getSteps();
        if (last == 0) { // init
            bag.setSteps(steps);
            bag.setLast(now);

            listener.message(now, "[BASELINE] %s", bag);

        } else if (s >= expect) {
            bag.setSteps(steps);
            bag.setLast(now);

            listener.message(now, "[GOOD] %,d steps in %,d minutes", s, elapse / 60_000L);

            WalkResult result = new WalkResult(last, now, s, true);
            listener.onResult(result);
            saveWalkResult(result);

            listener.message(now, "[RESET] %s", bag);

        } else if (elapse  > interval) {
            if (s < 0) { // init

            } else if (s < expect) { // underflow
                underflow = true;

                listener.message(now, "[NG] %,d steps in %,d minutes", s, elapse / 60_000L);

                WalkResult result = new WalkResult(last, now, s, false);
                listener.onResult(result);
                saveWalkResult(result);

            } else {
                listener.message(now, "[GOOD] %,d steps in %,d minutes", s, elapse / 60_000L);

                WalkResult result = new WalkResult(last, now, s, true);
                listener.onResult(result);
                saveWalkResult(result);
            }

            bag.setSteps(steps);
            bag.setLast(now);

            listener.message(now, "[RESET] %s", bag);

        } else {
            listener.message(now, "[MEASURE] %,d steps in %,d minutes", steps - bag.getSteps(), elapse / 60_000L);
        }

        return underflow;
    }

    class MiBandCallback extends MiBand.Callback {
        @Override
        protected void onReady(final MiBand mi) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();

                    try {
                        int steps = mi.getSteps();

                        listener.message(now, "[%s] Steps: %,d", mi.getId(), steps);

                        if (check(now, mi.getId(), steps)) {
                            listener.message(now, "Vibrating ...");
                            mi.setUserInfo(); // after this, you can vibrate the bracelet

                            for (int i = 0;i < 3;i++) {
                                mi.vibrate();
                                Thread.sleep(2000);
                            }
                        }
                    } catch (Exception ex) {
                        Log.e(T, ex.getMessage(), ex);

                    } finally {
                        mi.close();
                    }
                }
            });
        }
    }

    class MyBinder extends Binder {
        public MainService getService() {
            return MainService.this;
        }
    }
}
