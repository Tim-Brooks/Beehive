package fault;

import fault.messages.ResultMessage;
import fault.messages.ScheduleMessage;

import java.util.*;
import java.util.concurrent.FutureTask;

/**
 * Created by timbrooks on 12/9/14.
 */
public class ScheduleLoop {

    public boolean runLoop(ScheduleContext scheduleContext) {
        boolean didSomething = false;

        for (int i = 0; i < scheduleContext.poolSize; ++i) {
            if (handleScheduling(scheduleContext)) {
                didSomething = true;
            } else {
                break;
            }
        }

        for (int i = 0; i < scheduleContext.poolSize; ++i) {
            if (handleReturnResult(scheduleContext)) {
                didSomething = true;
            } else {
                break;
            }
        }

        long now = triggerTimeouts(scheduleContext);

        SortedMap<Long, List<ResultMessage<Object>>> tailView = scheduleContext.scheduled.tailMap(now);
        scheduleContext.scheduled = new TreeMap<>(tailView);

        return didSomething;
    }

    private boolean handleScheduling(ScheduleContext scheduleContext) {
        ScheduleMessage<Object> scheduleMessage = scheduleContext.toScheduleQueue.poll();
        if (scheduleMessage != null) {
            ActionCallable<Object> actionCallable = new ActionCallable<>(scheduleMessage.action, scheduleContext
                    .toReturnQueue);
            FutureTask<Void> futureTask = new FutureTask<>(actionCallable);
            ResilientTask<Object> resilientTask = new ResilientTask<>(futureTask, scheduleMessage.promise);
            ResultMessage<Object> resultMessage = actionCallable.resultMessage;
            scheduleContext.taskMap.put(resultMessage, resilientTask);

            scheduleContext.executorService.submit(resilientTask);
            scheduleTimeout(scheduleContext.scheduled, scheduleMessage.absoluteTimeout, resultMessage);
            return true;
        }
        return false;
    }

    private boolean handleReturnResult(ScheduleContext scheduleContext) {
        ResultMessage<Object> result = scheduleContext.toReturnQueue.poll();
        if (result != null) {
            if (ResultMessage.Type.ASYNC.equals(result.type)) {
                handleAsyncResult(scheduleContext, result);
                return true;
            } else {
                handleSyncResult(scheduleContext, result);
            }
        }
        return false;
    }

    private void handleSyncResult(ScheduleContext scheduleContext, ResultMessage<Object>
            result) {
        if (result.result != null) {
            scheduleContext.actionMetrics.reportActionResult(Status.SUCCESS);
        } else if (result.exception instanceof ActionTimeoutException) {
            scheduleTimeout(scheduleContext.scheduled, System.currentTimeMillis() - 1, result);
        } else {
            scheduleContext.actionMetrics.reportActionResult(Status.ERROR);
        }

    }

    private void handleAsyncResult(ScheduleContext scheduleContext,
                                   ResultMessage<Object> result) {
        ResilientTask<Object> resilientTask = scheduleContext.taskMap.remove(result);
        if (resilientTask != null) {

            ResilientPromise<Object> promise = resilientTask.resilientPromise;
            if (result.result != null) {
                promise.deliverResult(result.result);
            } else {
                promise.deliverError(result.exception);

            }
            scheduleContext.actionMetrics.reportActionResult(promise.status);
            scheduleContext.circuitBreaker.informBreakerOfResult(result.exception == null);
        }
    }

    private void scheduleTimeout(SortedMap<Long, List<ResultMessage<Object>>> scheduled, long absoluteTimeout,
                                 ResultMessage<Object> resultMessage) {
        if (scheduled.containsKey(absoluteTimeout)) {
            scheduled.get(absoluteTimeout).add(resultMessage);
        } else {
            List<ResultMessage<Object>> messages = new ArrayList<>();
            messages.add(resultMessage);
            scheduled.put(absoluteTimeout, messages);

        }
    }

    private long triggerTimeouts(ScheduleContext scheduleContext) {
        long now = System.currentTimeMillis();
        SortedMap<Long, List<ResultMessage<Object>>> toCancel = scheduleContext.scheduled.headMap(now);
        for (Map.Entry<Long, List<ResultMessage<Object>>> entry : toCancel.entrySet()) {
            List<ResultMessage<Object>> toTimeout = entry.getValue();
            for (ResultMessage<Object> messageToTimeout : toTimeout) {
                if (ResultMessage.Type.ASYNC.equals(messageToTimeout.type)) {
                    handleAsyncTimeout(scheduleContext, messageToTimeout);
                } else {
                    handleSyncTimeout(scheduleContext);
                }
            }
        }
        return now;
    }

    private void handleSyncTimeout(ScheduleContext scheduleContext) {
        scheduleContext.actionMetrics.reportActionResult(Status.TIMED_OUT);
        scheduleContext.circuitBreaker.informBreakerOfResult(false);
    }

    private void handleAsyncTimeout(ScheduleContext scheduleContext, ResultMessage<Object>
            resultMessage) {
        ResilientTask<Object> task = scheduleContext.taskMap.remove(resultMessage);
        if (task != null) {
            ResilientPromise<Object> promise = task.resilientPromise;
            if (!promise.isDone()) {
                promise.setTimedOut();
                task.cancel(true);
                scheduleContext.actionMetrics.reportActionResult(promise.status);
                scheduleContext.circuitBreaker.informBreakerOfResult(false);
            }
        }
    }
}
