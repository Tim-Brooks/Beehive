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
    private final ManagingRunnable managingRunnable;
    private final Thread managingThread;

    public ServiceExecutor(int poolSize) {
        this(poolSize, new ActionMetrics(3600));
    }

    public ServiceExecutor(int poolSize, IActionMetrics actionMetrics) {
        this(poolSize, actionMetrics, new DefaultCircuitBreaker(actionMetrics, new BreakerConfig.BreakerConfigBuilder().failureThreshold(20)
                .timePeriodInMillis(5000).build()));
    }

    public ServiceExecutor(int poolSize, IActionMetrics actionMetrics, ICircuitBreaker circuitBreaker) {
        this(actionMetrics, circuitBreaker, new ManagingRunnable(poolSize, circuitBreaker, actionMetrics));
    }
    public ServiceExecutor(IActionMetrics actionMetrics, ICircuitBreaker circuitBreaker,
                           ManagingRunnable managingRunnable) {
        this.circuitBreaker = circuitBreaker;
        this.managingRunnable = managingRunnable;
        managingThread = new Thread(managingRunnable, "Action Managing Thread");
        managingThread.start();
    }

    public <T> ResilientPromise<T> performAction(ResilientAction<T> action, int millisTimeout) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }
        long absoluteTimeout = millisTimeout + 1 + System.currentTimeMillis();
        final ResilientPromise<T> resilientPromise = new ResilientPromise<>();

        ScheduleMessage<T> e = new ScheduleMessage<>(action, resilientPromise, absoluteTimeout);
        managingRunnable.submit(e);

        return resilientPromise;
    }

    public <T> ResilientPromise<T> performSyncAction(ResilientAction<T> action) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }

        return managingRunnable.execute(action);
    }

    public void shutdown() {
        managingRunnable.shutdown();
        managingThread.interrupt();
    }

}
