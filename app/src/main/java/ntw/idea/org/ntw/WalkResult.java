package ntw.idea.org.ntw;

/**
 * Created by rickwang on 2015/06/23.
 */
public class WalkResult {
    final long start;
    final long end;
    final int steps;
    final boolean success;

    public WalkResult(long start, long end, int steps, boolean success) {
        this.start = start;
        this.end = end;
        this.steps = steps;
        this.success = success;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public int getSteps() {
        return steps;
    }

    public boolean isSuccess() {
        return success;
    }
}
