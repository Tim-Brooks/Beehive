package fault.concurrent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by timbrooks on 1/12/15.
 */
public class ExecutorSemaphore {

    private final AtomicInteger permitsRemaining;
    private final ConcurrentMap<Permit, Boolean> permitHolders = new ConcurrentHashMap<>();

    public ExecutorSemaphore(int concurrencyLevel) {
        permitsRemaining = new AtomicInteger(concurrencyLevel);
    }

    public Permit acquirePermit() {
        for (; ; ) {
            int permitsRemaining = this.permitsRemaining.get();
            if (permitsRemaining > 0) {
                if (this.permitsRemaining.compareAndSet(permitsRemaining, permitsRemaining - 1)) {
                    Permit permit = new Permit();
                    permitHolders.put(permit, true);
                    return permit;
                }
            } else {
                return null;
            }
        }
    }

    public void releasePermit(Permit permit) {
        if (permitHolders.remove(permit) != null) {
            this.permitsRemaining.incrementAndGet();
        }
    }

    public final class Permit {
    }
}
