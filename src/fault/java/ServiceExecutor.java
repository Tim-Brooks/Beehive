package fault.java;

import fault.java.circuit.BreakerConfig;
import fault.java.circuit.ICircuitBreaker;
import fault.java.circuit.DefaultCircuitBreaker;
import fault.java.messages.ScheduleMessage;
import fault.java.metrics.ActionMetrics;
import fault.java.metrics.IActionMetrics;

/**
 * Created by timbrooks on 11/13/14.
 */
public class ServiceExecutor {

    private final ICircuitBreaker circuitBreaker;
    private Thread managingThread;
    private ManagingRunnable managingRunnable;

    public ServiceExecutor(int poolSize) {
        this(poolSize, new ActionMetrics());
    }

    public ServiceExecutor(int poolSize, IActionMetrics actionMetrics) {
        this(poolSize, actionMetrics, new DefaultCircuitBreaker(actionMetrics, new BreakerConfig.BreakerConfigBuilder().failureThreshold(20)
                .timePeriodInMillis(5000).build()));
    }

    public ServiceExecutor(int poolSize, IActionMetrics actionMetrics, ICircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        managingRunnable = new ManagingRunnable(poolSize, circuitBreaker, actionMetrics);
        managingThread = new Thread(managingRunnable, "Action Managing Thread");
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
