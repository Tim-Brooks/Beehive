package fault.java.circuit;

import fault.java.metrics.IActionMetrics;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/5/14.
 */
public class DefaultCircuitBreaker implements ICircuitBreaker {

    private boolean circuitOpen;
    private AtomicReference<BreakerConfig> breakerConfig;
    private final IActionMetrics actionMetrics;

    public DefaultCircuitBreaker(IActionMetrics actionMetrics, BreakerConfig breakerConfig) {
        this.actionMetrics = actionMetrics;
        this.circuitOpen = false;
        this.breakerConfig = new AtomicReference<>(breakerConfig);
    }

    @Override
    public boolean isOpen() {
        return circuitOpen;
    }

    @Override
    public void informBreakerOfResult(boolean successful) {
        if (successful) {
            if (circuitOpen) {
                circuitOpen = false;
            }
        } else {
            if (!circuitOpen) {
                BreakerConfig config = this.breakerConfig.get();
                int failuresForTimePeriod = actionMetrics.getFailuresForTimePeriod(config.timePeriodInMillis);
                if (config.failureThreshold < failuresForTimePeriod) {
                    circuitOpen = true;
                }
            }
        }
    }

    @Override
    public void setBreakerConfig(BreakerConfig breakerConfig) {
        this.breakerConfig.set(breakerConfig);
    }
}
