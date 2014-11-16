package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.ResilientResult;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.*;

/**
 * Created by timbrooks on 11/13/14.
 */
public class ManagingRunnable implements Runnable {

    private final CircuitBreaker circuitBreaker;
    private final ActionMetrics actionMetrics;
    private final ConcurrentLinkedQueue<ActionCallable<?>> toScheduleQueue;
    private final ConcurrentLinkedQueue<ResilientResult<?>> toReturnQueue;
    private final ScheduledExecutorService executorService;
    private volatile boolean isRunning;

    public ManagingRunnable(int poolSize, CircuitBreaker circuitBreaker, ActionMetrics actionMetrics,
                            ConcurrentLinkedQueue<ActionCallable<?>> toScheduleQueue,
                            ConcurrentLinkedQueue<ResilientResult<?>> toReturnQueue) {
        this.circuitBreaker = circuitBreaker;
        this.actionMetrics = actionMetrics;
        this.toScheduleQueue = toScheduleQueue;
        this.toReturnQueue = toReturnQueue;
        this.executorService = Executors.newScheduledThreadPool(poolSize);
    }

    @Override
    public void run() {
        NavigableMap<Long, ScheduledFuture<?>> scheduled = new TreeMap<>();
        isRunning = true;
        while (isRunning) {
            ActionCallable<?> actionCallable = toScheduleQueue.poll();
            if (actionCallable != null) {
                ScheduledFuture<?> future = executorService.schedule(actionCallable, 0, TimeUnit.MILLISECONDS);
                scheduled.put(actionCallable.timeout, future);
            }
            ResilientResult<?> result = toReturnQueue.poll();
            if (result != null) {
                actionMetrics.informActionOfResult(result);
                circuitBreaker.informBreakerOfResult(result.isSuccessful());
            }


        }

    }

    public void shutdown() {
        isRunning = false;
    }
}
