package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.ResilientTask;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by timbrooks on 11/13/14.
 */
public class ManagingRunnable implements Runnable {

    private final CircuitBreaker circuitBreaker;
    private final ActionMetrics actionMetrics;
    private final ConcurrentLinkedQueue<ScheduleMessage<?>> toScheduleQueue;
    private final ConcurrentLinkedQueue<ResultMessage<?>> toReturnQueue;
    private final ExecutorService executorService;
    private volatile boolean isRunning;

    public ManagingRunnable(int poolSize, CircuitBreaker circuitBreaker, ActionMetrics actionMetrics,
                            ConcurrentLinkedQueue<ScheduleMessage<?>> toScheduleQueue,
                            ConcurrentLinkedQueue<ResultMessage<?>> toReturnQueue) {
        this.circuitBreaker = circuitBreaker;
        this.actionMetrics = actionMetrics;
        this.toScheduleQueue = toScheduleQueue;
        this.toReturnQueue = toReturnQueue;
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public void run() {
        SortedMap<Long, ResilientTask<?>> scheduled = new TreeMap<>();
        Map<ResultMessage<?>, ResilientTask<?>> promiseMap = new HashMap<>();
        isRunning = true;
        while (isRunning) {
            boolean didSomething = false;

            ScheduleMessage<?> scheduleMessage = toScheduleQueue.poll();
            if (scheduleMessage != null) {
                ActionCallable<?> actionCallable = new ActionCallable<>(scheduleMessage.action, toReturnQueue);
                FutureTask<Void> futureTask = new FutureTask<>(actionCallable);
                ResilientTask<?> resilientTask = new ResilientTask<>(futureTask, scheduleMessage.promise);
                promiseMap.put(actionCallable.resultMessage, resilientTask);

                executorService.submit(resilientTask);
                scheduled.put(scheduleMessage.relativeTimeout, resilientTask);
                didSomething = true;
            }

            ResultMessage<?> result = toReturnQueue.poll();
            if (result != null) {
                ResilientTask<?> resilientTask = promiseMap.remove(result);
                actionMetrics.logActionResult(resilientTask);
                circuitBreaker.informBreakerOfResult(resilientTask.isSuccessful());
                didSomething = true;
            }

            long now = System.currentTimeMillis();
            SortedMap<Long, ResilientTask<?>> toCancel = scheduled.headMap(now);
            for (Map.Entry<Long, ResilientTask<?>> entry : toCancel.entrySet()) {
                ResilientTask<?> resilientTask = entry.getValue();
                if (!resilientTask.isDone()) {
                    resilientTask.cancel(true);
                    actionMetrics.logActionResult(resilientTask);
                    circuitBreaker.informBreakerOfResult(resilientTask.isSuccessful());
                }
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
