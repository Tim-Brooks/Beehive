package fault.timeout;

import fault.ResilientCallback;
import fault.concurrent.ResilientPromise;
import fault.concurrent.ExecutorSemaphore;

import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created by timbrooks on 5/30/15.
 */
public class ActionTimeout implements Delayed {

    public final long millisAbsoluteTimeout;
    public final ExecutorSemaphore.Permit permit;
    public final ResilientPromise<?> promise;
    public final ResilientCallback<?> callback;
    public final Future<Void> future;

    public ActionTimeout(ExecutorSemaphore.Permit permit, ResilientPromise<?> promise, long millisRelativeTimeout,
                         Future<Void> future, ResilientCallback<?> callback) {
        this.permit = permit;
        this.promise = promise;
        this.callback = callback;
        this.millisAbsoluteTimeout = millisRelativeTimeout + System.currentTimeMillis();
        this.future = future;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(millisAbsoluteTimeout - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (o instanceof ActionTimeout) {
            return Long.compare(millisAbsoluteTimeout, ((ActionTimeout) o).millisAbsoluteTimeout);
        }
        return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }
}
