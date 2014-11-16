package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.ResilientAction;
import fault.java.circuit.BreakerConfig;
import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.CircuitBreakerImplementation;
import fault.java.circuit.ResilientResult;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by timbrooks on 11/13/14.
 */
public class SingleWriterServiceExecutor {

    private final CircuitBreaker circuitBreaker;
    private final ActionMetrics actionMetrics;
    private final ConcurrentLinkedQueue<ActionCallable<?>> toScheduleQueue;
    private final ConcurrentLinkedQueue<ResilientResult<?>> toReturnQueue;
    private final int poolSize;
    private Thread managingThread;
    private ManagingRunnable managingRunnable;

    public SingleWriterServiceExecutor(int poolSize) {
        this.poolSize = poolSize;
        this.actionMetrics = new ActionMetrics();
        this.circuitBreaker = new CircuitBreakerImplementation(actionMetrics, new BreakerConfig());
        this.toScheduleQueue = new ConcurrentLinkedQueue<>();
        this.toReturnQueue = new ConcurrentLinkedQueue<>();
    }

    public <T> ResilientResult<T> performAction(final ResilientAction<T> action, int millisTimeout) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }
        final ResilientResult<T> resilientResult = new ResilientResult<>();
        long relativeTimeout = millisTimeout + 1 + System.currentTimeMillis();
        toScheduleQueue.add(new ActionCallable<>(action, relativeTimeout,
                resilientResult,
                toReturnQueue));

        return resilientResult;
    }

    public void shutdown() {
        managingRunnable.shutdown();
        managingThread.interrupt();
    }

    private void startManagerThread() {
        // TODO: Name thread.
        managingRunnable = new ManagingRunnable(poolSize, circuitBreaker, actionMetrics, toScheduleQueue,
                toReturnQueue);
        managingThread = new Thread(managingRunnable);
        managingThread.start();
    }
}
