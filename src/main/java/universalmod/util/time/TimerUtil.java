package universalmod.util.time;

public class TimerUtil {
    private long lastMS = System.currentTimeMillis();
    private long startTime;

    public TimerUtil() {
        resetCounter();
    }

    public static TimerUtil create() {
        return new TimerUtil();
    }

    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    public void resetCounter() {
        lastMS = System.currentTimeMillis();
    }

    public boolean isReached(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public void setLastMS(long newValue) {
        lastMS = System.currentTimeMillis() + newValue;
    }

    public void setTime(long time) {
        lastMS = time;
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public boolean isRunning() {
        return System.currentTimeMillis() - lastMS <= 0;
    }

    public boolean hasTimeElapsed(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public boolean finished(double delay) {
        return System.currentTimeMillis() - delay >= startTime;
    }

    public boolean hasTimeElapsed() {
        return lastMS < System.currentTimeMillis();
    }
}
