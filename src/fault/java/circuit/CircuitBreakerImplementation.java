package fault.java.circuit;

import fault.java.ActionMetrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/5/14.
 */
public class CircuitBreakerImplementation implements CircuitBreaker {

    private final AtomicBoolean circuitOpen;
    private final ActionMetrics actionMetrics;
    private AtomicReference<BreakerConfig> breakerConfig;

    public CircuitBreakerImplementation(ActionMetrics actionMetrics, BreakerConfig breakerConfig) {
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
                circuitOpen.set(true);
            }
        } else {
            if (!circuitOpen.get()) {
                BreakerConfig config = this.breakerConfig.get();
                if (config.failureThreshold < actionMetrics.getFailuresForTimePeriod(config.timePeriodInMillis)) {
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
