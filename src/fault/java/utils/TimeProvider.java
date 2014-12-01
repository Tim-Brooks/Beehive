package fault.java.utils;

/**
 * Created by timbrooks on 11/23/14.
 */
public final class TimeProvider {

    public final long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public final long nanoTime() {
        return System.nanoTime();
    }
}
