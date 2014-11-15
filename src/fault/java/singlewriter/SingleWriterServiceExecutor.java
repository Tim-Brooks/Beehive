package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.ResilientAction;
import fault.java.TimeoutService;
import fault.java.circuit.BreakerConfig;
import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.CircuitBreakerImplementation;
import fault.java.circuit.ResilientResult;

import java.util.concurrent.*;

/**
 * Created by timbrooks on 11/13/14.
 */
public class SingleWriterServiceExecutor {

    private final CircuitBreaker circuitBreaker;
    private final ActionMetrics actionMetrics;
    private final ConcurrentLinkedQueue<Runnable> toScheduleQueue;
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
        toScheduleQueue.add(new ActionCallable<>(action, resilientResult));

        return resilientResult;
    }

    public void shutdown() {
        managingRunnable.shutdown();
        managingThread.interrupt();
    }

    private void startManagerThread() {
        // TODO: Name thread.
        managingRunnable = new ManagingRunnable(poolSize, toScheduleQueue, toReturnQueue);
        managingThread = new Thread(managingRunnable);
        managingThread.start();
    }
}
