package ntw.idea.org.ntw;

/**
 * Created by rickwang on 2015/06/16.
 */
public class MiBandBag {
    final String mac;
    long last;
    int steps;

    public MiBandBag(String mac) {
        this.mac = mac;
    }

    public String getMac() {
        return mac;
    }

    public long getLast() {
        return last;
    }

    public void setLast(long last) {
        this.last = last;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    @Override
    public String toString() {
        return String.format("[%s] %,d", mac, steps);
    }
}
