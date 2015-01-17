package fault;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by timbrooks on 1/12/15.
 */
public class Semaphore {

    public final AtomicInteger permitsRemaining;

    public Semaphore(int concurrencyLevel) {
        permitsRemaining = new AtomicInteger(concurrencyLevel);
    }

    public boolean acquirePermit() {
        int permitsRemaining = this.permitsRemaining.get();
        for (; ; ) {
            if (permitsRemaining > 0) {
                if (this.permitsRemaining.compareAndSet(permitsRemaining, permitsRemaining - 1)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public void releasePermit() {
        this.permitsRemaining.incrementAndGet();
    }
}
