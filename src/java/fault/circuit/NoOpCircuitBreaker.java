package fault.circuit;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by timbrooks on 11/5/14.
 */
public class NoOpCircuitBreaker implements CircuitBreaker {
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);

    @Override
    public boolean isOpen() {
        return circuitOpen.get();
    }

    @Override
    public boolean allowAction() {
        return !isOpen();
    }

    @Override
    public void informBreakerOfResult(boolean successful) {
    }

    @Override
    public BreakerConfig getBreakerConfig() {
        return null;
    }

    @Override
    public void setBreakerConfig(BreakerConfig breakerConfig) {

    }

    @Override
    public void forceOpen() {
        circuitOpen.set(true);
    }

    @Override
    public void forceClosed() {
        circuitOpen.set(false);
    }
}
