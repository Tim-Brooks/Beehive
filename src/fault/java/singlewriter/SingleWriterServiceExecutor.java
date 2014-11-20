package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.ResilientAction;
import fault.java.circuit.BreakerConfig;
import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.CircuitBreakerImplementation;

/**
 * Created by timbrooks on 11/13/14.
 */
public class SingleWriterServiceExecutor {

    private final CircuitBreaker circuitBreaker;
    private Thread managingThread;
    private ManagingRunnable managingRunnable;

    public SingleWriterServiceExecutor(int poolSize) {
        ActionMetrics actionMetrics = new ActionMetrics();

        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(20)
                .timePeriodInMillis(5000).build();
        this.circuitBreaker = new CircuitBreakerImplementation(actionMetrics, breakerConfig);

        managingRunnable = new ManagingRunnable(poolSize, circuitBreaker, actionMetrics);
        managingThread = new Thread(managingRunnable);
        managingThread.start();
    }

    public <T> ResilientPromise<T> performAction(final ResilientAction<T> action, int millisTimeout) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }
        long relativeTimeout = millisTimeout + 1 + System.currentTimeMillis();
        final ResilientPromise<T> resilientPromise = new ResilientPromise<>();

        ScheduleMessage<T> e = new ScheduleMessage<>(action, resilientPromise, relativeTimeout);
        managingRunnable.submit(e);

        return resilientPromise;
    }

    public void shutdown() {
        managingRunnable.shutdown();
        managingThread.interrupt();
    }

}
