package ntw.idea.org.ntw;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

public class MainActivity extends ActionBarActivity implements ResultListener {
    static final String T = "main";

    Handler handler;
    TextView display;
    WallView wall;

    MainService service;

    ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MainService.MyBinder binder = (MainService.MyBinder) service;
            MainActivity.this.service = binder.getService();
            MainActivity.this.service.setResultListener(MainActivity.this);

            Log.i(T, "Service is connected");

            List<WalkResult> results = MainActivity.this.service.getWalkResults();
            synchronized (results) {
                for (WalkResult result : results) {
                    onResult(result);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    protected void startService() {
        Intent intent = new Intent(this, MainService.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startService(intent);
        bindService(intent, connection, Context.BIND_ADJUST_WITH_ACTIVITY);
    }

    protected void stopService() {
        service.unsetResultListener(); // remove the callback

        unbindService(connection);

        Log.i(T, "Service is unbound");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();
        display = (TextView) findViewById(R.id.display);
        wall = (WallView) findViewById(R.id.wall);

        startService();
    }

    @Override
    protected void onDestroy() {
        stopService();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ======

    @Override
    public void message(long timestamp, String format, Object... args) {
        final String s = String.format("[%s] %s", Constants.DATETIME_FORMAT.format(new Date(timestamp)), String.format(format, args));
        handler.post(new Runnable() {
            @Override
            public void run() {
                display.append(s);
                display.append("\r\n");

                Log.i(T, s);
            }
        });
    }

    @Override
    public void onProcess() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                display.setText("");
            }
        });
    }

    @Override
    public void onResult(final WalkResult result) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                wall.addWalkResult(result);
            }
        });
    }

    // ======

    public void onVibrate(View view) {
        service.process();
    }
}
