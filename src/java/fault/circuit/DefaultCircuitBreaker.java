package fault.circuit;

import fault.metrics.ActionMetrics;
import fault.utils.TimeProvider;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by timbrooks on 11/5/14.
 */
public class DefaultCircuitBreaker implements CircuitBreaker {

    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int FORCED_OPEN = 2;

    private final TimeProvider timeProvider;
    private final ActionMetrics actionMetrics;
    private final AtomicInteger state = new AtomicInteger(0);
    private AtomicLong lastTestedTime = new AtomicLong(0);
    private AtomicReference<BreakerConfig> breakerConfig;

    public DefaultCircuitBreaker(ActionMetrics actionMetrics, BreakerConfig breakerConfig) {
        this(actionMetrics, breakerConfig, new TimeProvider());
    }

    public DefaultCircuitBreaker(ActionMetrics actionMetrics, BreakerConfig breakerConfig, TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        this.actionMetrics = actionMetrics;
        this.breakerConfig = new AtomicReference<>(breakerConfig);
    }

    @Override
    public boolean isOpen() {
        return state.get() != CLOSED;
    }

    @Override
    public boolean allowAction() {
        int state = this.state.get();
        if (state == OPEN) {
            long timeToPauseMillis = breakerConfig.get().timeToPauseMillis;
            long currentTime = timeProvider.currentTimeMillis();
            // This potentially allows a couple of tests through. Should think about this decision
            if (currentTime < timeToPauseMillis + lastTestedTime.get()) {
                return false;
            }
            lastTestedTime.set(currentTime);
        }
        return state != FORCED_OPEN;
    }

    @Override
    public void informBreakerOfResult(boolean successful) {
        if (successful) {
            if (state.get() == OPEN) {
                // This can get stuck in a loop with open and closing
                state.compareAndSet(OPEN, CLOSED);
            }
        } else {
            if (state.get() == CLOSED) {
                BreakerConfig config = this.breakerConfig.get();
                int failuresForTimePeriod = actionMetrics.getFailuresForTimePeriod(config.timePeriodInMillis);
                if (config.failureThreshold < failuresForTimePeriod) {
                    lastTestedTime.set(timeProvider.currentTimeMillis());
                    state.compareAndSet(CLOSED, OPEN);
                }
            }
        }
    }

    @Override
    public void setBreakerConfig(BreakerConfig breakerConfig) {
        this.breakerConfig.set(breakerConfig);
    }

    @Override
    public BreakerConfig getBreakerConfig() {
        return breakerConfig.get();
    }

    @Override
    public void forceOpen() {
        state.set(FORCED_OPEN);
    }

    @Override
    public void forceClosed() {
        state.set(CLOSED);
    }
}
