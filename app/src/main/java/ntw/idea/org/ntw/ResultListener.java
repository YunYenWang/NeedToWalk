package ntw.idea.org.ntw;

/**
 * Created by rickwang on 2015/06/23.
 */
public interface ResultListener {

    void message(long timestamp, String format, Object... args);

    void onProcess();

    void onResult(WalkResult result);
}
