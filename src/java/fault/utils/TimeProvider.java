package fault.utils;

/**
 * Created by timbrooks on 11/23/14.
 */
public class TimeProvider {

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public long nanoTime() {
        return System.nanoTime();
    }
}
