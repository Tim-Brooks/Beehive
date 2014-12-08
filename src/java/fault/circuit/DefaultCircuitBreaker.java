package fault.circuit;

import fault.metrics.IActionMetrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/5/14.
 */
public class DefaultCircuitBreaker implements ICircuitBreaker {

    private AtomicBoolean circuitOpen;
    private AtomicReference<BreakerConfig> breakerConfig;
    private final IActionMetrics actionMetrics;

    public DefaultCircuitBreaker(IActionMetrics actionMetrics, BreakerConfig breakerConfig) {
        this.actionMetrics = actionMetrics;
        this.circuitOpen = new AtomicBoolean(false);
        this.breakerConfig = new AtomicReference<>(breakerConfig);
    }

    @Override
    public boolean isOpen() {
        return circuitOpen.get();
    }

    @Override
    public void informBreakerOfResult(boolean successful) {
        if (successful) {
            if (circuitOpen.get()) {
                circuitOpen.compareAndSet(true, false);
            }
        } else {
            if (!circuitOpen.get()) {
                BreakerConfig config = this.breakerConfig.get();
                int failuresForTimePeriod = actionMetrics.getFailuresForTimePeriod(config.timePeriodInMillis);
                if (config.failureThreshold < failuresForTimePeriod) {
                    circuitOpen.compareAndSet(false, true);
                }
            }
        }
    }

    @Override
    public void setBreakerConfig(BreakerConfig breakerConfig) {
        this.breakerConfig.set(breakerConfig);
    }
}
