package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.ResilientTask;
import fault.java.circuit.CircuitBreaker;

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
    private final ConcurrentLinkedQueue<ScheduleMessage<Object>> toScheduleQueue;
    private final ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue;
    private final ExecutorService executorService;
    private volatile boolean isRunning;

    public ManagingRunnable(int poolSize, CircuitBreaker circuitBreaker, ActionMetrics actionMetrics) {
        this.circuitBreaker = circuitBreaker;
        this.actionMetrics = actionMetrics;
        this.toScheduleQueue = new ConcurrentLinkedQueue<>();
        this.toReturnQueue = new ConcurrentLinkedQueue<>();
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public void run() {
        SortedMap<Long, ResultMessage<Object>> scheduled = new TreeMap<>();
        Map<ResultMessage<Object>, ResilientTask<Object>> taskMap = new HashMap<>();
        isRunning = true;
        while (isRunning) {
            boolean didSomething = false;

            ScheduleMessage<Object> scheduleMessage = toScheduleQueue.poll();
            if (scheduleMessage != null) {
                ActionCallable<Object> actionCallable = new ActionCallable<>(scheduleMessage.action, toReturnQueue);
                FutureTask<Void> futureTask = new FutureTask<>(actionCallable);
                ResilientTask<Object> resilientTask = new ResilientTask<>(futureTask, scheduleMessage.promise);
                ResultMessage<Object> resultMessage = actionCallable.resultMessage;
                taskMap.put(resultMessage, resilientTask);

                executorService.submit(resilientTask);
                scheduled.put(scheduleMessage.relativeTimeout, resultMessage);
                didSomething = true;
            }

            ResultMessage<Object> result = toReturnQueue.poll();
            if (result != null) {
                handleResult(taskMap, result);
                didSomething = true;
            }

            long now = System.currentTimeMillis();
            SortedMap<Long, ResultMessage<Object>> toCancel = scheduled.headMap(now);
            for (Map.Entry<Long, ResultMessage<Object>> entry : toCancel.entrySet()) {
                handleTimeout(taskMap, entry.getValue());
            }

            SortedMap<Long, ResultMessage<Object>> tailView = scheduled.tailMap(now);
            scheduled = new TreeMap<>(tailView);

            if (!didSomething) {
                LockSupport.parkNanos(1);
            }

        }

    }

    @SuppressWarnings("unchecked")
    public <T> void submit(ScheduleMessage<T> message) {
        toScheduleQueue.offer((ScheduleMessage<Object>) message);
    }

    private void handleResult(Map<ResultMessage<Object>, ResilientTask<Object>> taskMap, ResultMessage<Object> result) {
        ResilientTask<Object> resilientTask = taskMap.remove(result);
        if (resilientTask != null) {

            ResilientPromise<Object> promise = resilientTask.resilientPromise;
            if (result.result != null) {
                promise.deliverResult(result.result);
            } else {
                promise.deliverError(result.exception);
            }
            actionMetrics.logActionResult(promise);
            circuitBreaker.informBreakerOfResult(result.exception == null);
        }
    }

    private void handleTimeout(Map<ResultMessage<Object>, ResilientTask<Object>> taskMap, ResultMessage<Object>
            resultMessage) {
        ResilientTask<Object> task = taskMap.remove(resultMessage);
        if (task != null) {
            ResilientPromise<Object> promise = task.resilientPromise;
            if (!promise.isDone()) {
                promise.setTimedOut();
                task.cancel(true);
                actionMetrics.logActionResult(promise);
                circuitBreaker.informBreakerOfResult(false);
            }
        }
    }

    public void shutdown() {
        isRunning = false;
    }
}
