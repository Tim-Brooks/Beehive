package fault.java.circuit;

import fault.java.metrics.IActionMetrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/5/14.
 */
public class DefaultCircuitBreaker implements ICircuitBreaker {

    private final AtomicBoolean circuitOpen;
    // TODO With single writer this will not need to be Atomic
    private AtomicReference<BreakerConfig> breakerConfig;
    private final IActionMetrics IActionMetrics;

    public DefaultCircuitBreaker(IActionMetrics IActionMetrics, BreakerConfig breakerConfig) {
        this.IActionMetrics = IActionMetrics;
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
                circuitOpen.set(false);
            }
        } else {
            if (!circuitOpen.get()) {
                BreakerConfig config = this.breakerConfig.get();
                int failuresForTimePeriod = IActionMetrics.getFailuresForTimePeriod(config.timePeriodInMillis);
                if (config.failureThreshold < failuresForTimePeriod) {
                    circuitOpen.set(true);
                }
            }
        }
    }

    @Override
    public void setBreakerConfig(BreakerConfig breakerConfig) {
        this.breakerConfig.set(breakerConfig);
    }
}
