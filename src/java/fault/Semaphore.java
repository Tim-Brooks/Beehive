package fault;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by timbrooks on 1/12/15.
 */
public class Semaphore {

    private final AtomicInteger permitsRemaining;
    private final ConcurrentMap<Permit, Boolean> permitHolders = new ConcurrentHashMap<>();

    public Semaphore(int concurrencyLevel) {
        permitsRemaining = new AtomicInteger(concurrencyLevel);
    }

    public Permit acquirePermit() {
        int permitsRemaining = this.permitsRemaining.get();
        for (; ; ) {
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
        if (permitHolders.remove(permit)) {
            this.permitsRemaining.incrementAndGet();
        }
    }

    final class Permit {
    }
}
