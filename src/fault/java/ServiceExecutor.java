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

    private final ICircuitBreaker ICircuitBreaker;
    private Thread managingThread;
    private ManagingRunnable managingRunnable;

    public ServiceExecutor(int poolSize) {
        IActionMetrics IActionMetrics = new ActionMetrics();

        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(20)
                .timePeriodInMillis(5000).build();
        this.ICircuitBreaker = new DefaultCircuitBreaker(IActionMetrics, breakerConfig);

        managingRunnable = new ManagingRunnable(poolSize, ICircuitBreaker, IActionMetrics);
        managingThread = new Thread(managingRunnable, "Action Managing Thread");
        managingThread.start();
    }

    public <T> ResilientPromise<T> performAction(final ResilientAction<T> action, int millisTimeout) {
        if (ICircuitBreaker.isOpen()) {
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
