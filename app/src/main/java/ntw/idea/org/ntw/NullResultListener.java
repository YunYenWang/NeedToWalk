package ntw.idea.org.ntw;

import android.util.Log;

import java.util.Date;

/**
 * Created by rickwang on 2015/06/23.
 */
public class NullResultListener implements ResultListener {
    static final String T = "MiBand";

    @Override
    public void message(long timestamp, String format, Object... args) {
        final String s = String.format("[%s] %s", Constants.DATETIME_FORMAT.format(new Date(timestamp)), String.format(format, args));
        Log.i(T, s);
    }

    @Override
    public void onProcess() {
    }

    @Override
    public void onResult(WalkResult result) {
    }
}
