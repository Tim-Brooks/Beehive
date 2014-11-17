package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.ResilientAction;
import fault.java.circuit.BreakerConfig;
import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.CircuitBreakerImplementation;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by timbrooks on 11/13/14.
 */
public class SingleWriterServiceExecutor {

    private final CircuitBreaker circuitBreaker;
    private final ConcurrentLinkedQueue<ScheduleMessage<?>> toScheduleQueue;
    private final ConcurrentLinkedQueue<ResultMessage<?>> toReturnQueue;
    private Thread managingThread;
    private ManagingRunnable managingRunnable;

    public SingleWriterServiceExecutor(int poolSize) {
        ActionMetrics actionMetrics = new ActionMetrics();

        this.circuitBreaker = new CircuitBreakerImplementation(actionMetrics, new BreakerConfig());
        this.toScheduleQueue = new ConcurrentLinkedQueue<>();
        this.toReturnQueue = new ConcurrentLinkedQueue<>();

        managingRunnable = new ManagingRunnable(poolSize, circuitBreaker, actionMetrics, toScheduleQueue,
                toReturnQueue);
        managingThread = new Thread(managingRunnable);
        managingThread.start();
    }

    public <T> ResilientPromise<T> performAction(final ResilientAction<T> action, int millisTimeout) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }
        long relativeTimeout = millisTimeout + 1 + System.currentTimeMillis();
        final ResilientPromise<T> resilientPromise = new ResilientPromise<>();

        toScheduleQueue.add(new ScheduleMessage<>(action, resilientPromise, relativeTimeout));

        return resilientPromise;
    }

    public void shutdown() {
        managingRunnable.shutdown();
        managingThread.interrupt();
    }

}
