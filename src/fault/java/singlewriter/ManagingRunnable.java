package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.ResilientResult;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

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
        SortedMap<Long, ScheduledFuture<?>> scheduled = new TreeMap<>();
        isRunning = true;
        while (isRunning) {
            boolean didSomething = false;

            ActionCallable<?> actionCallable = toScheduleQueue.poll();
            if (actionCallable != null) {
                ScheduledFuture<?> future = executorService.schedule(actionCallable, 0, TimeUnit.MILLISECONDS);
                scheduled.put(actionCallable.relativeTimeout, future);
                didSomething = true;
            }
            ResilientResult<?> result = toReturnQueue.poll();
            if (result != null) {
                actionMetrics.informActionOfResult(result);
                circuitBreaker.informBreakerOfResult(result.isSuccessful());
                didSomething = true;
            }

            // Need to indicate on Resilient Result that it is timed-out.
            long now = System.currentTimeMillis();
            SortedMap<Long, ScheduledFuture<?>> toCancel = scheduled.headMap(now);
            for (Map.Entry<Long, ScheduledFuture<?>> entry : toCancel.entrySet()) {
                entry.getValue().cancel(true);
            }
            scheduled = scheduled.tailMap(now);

            if (!didSomething) {
                LockSupport.parkNanos(1);
            }

        }

    }

    public void shutdown() {
        isRunning = false;
    }
}
