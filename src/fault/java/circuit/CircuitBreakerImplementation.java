package fault.java.circuit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by timbrooks on 11/5/14.
 */
public class CircuitBreakerImplementation implements CircuitBreaker {

    private final AtomicBoolean circuitOpen;
    private final AtomicInteger failedCount;
    private final int threshold;

    public CircuitBreakerImplementation() {
        this.circuitOpen = new AtomicBoolean(false);
        this.failedCount = new AtomicInteger(0);
        this.threshold = 20;
    }

    @Override
    public boolean isOpen() {
        return circuitOpen.get();
    }

    @Override
    public void informBreakerOfResult(boolean successful) {
        if (successful) {
            if (circuitOpen.get()) {
                circuitOpen.set(true);
            }
        } else {
            if (!circuitOpen.get()) {
                int failures = failedCount.incrementAndGet();
                if (threshold < failures) {
                    circuitOpen.set(true);
                }
            }
        }
    }
}
