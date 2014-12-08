package fault.circuit;

import fault.metrics.IActionMetrics;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/5/14.
 */
public class DefaultCircuitBreaker implements ICircuitBreaker {

    private AtomicBoolean circuitOpen;
    private AtomicReference<BreakerConfig> breakerConfig;
    private AtomicLong lastTestedTime;
    private final IActionMetrics actionMetrics;

    public DefaultCircuitBreaker(IActionMetrics actionMetrics, BreakerConfig breakerConfig) {
        this.actionMetrics = actionMetrics;
        this.circuitOpen = new AtomicBoolean(false);
        this.lastTestedTime = new AtomicLong(0);
        this.breakerConfig = new AtomicReference<>(breakerConfig);
    }

    @Override
    public boolean isOpen() {
        return circuitOpen.get();
    }

    @Override
    public boolean allowAction() {
        if (isOpen()) {
            long timeToPauseMillis = breakerConfig.get().timeToPauseMillis;
            long currentTime = System.currentTimeMillis();
            // This potentially allows a couple of tests through. Should think about this decision
            if (currentTime < timeToPauseMillis + lastTestedTime.get()) {
                return false;
            }
            lastTestedTime.set(currentTime);
        }
        return true;
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
                    lastTestedTime.set(System.currentTimeMillis());
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
