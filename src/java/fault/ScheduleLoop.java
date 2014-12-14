package fault;

import fault.messages.ResultMessage;
import fault.messages.ScheduleMessage;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.FutureTask;

/**
 * Created by timbrooks on 12/9/14.
 */
public class ScheduleLoop {

    public static boolean runLoop(ScheduleContext scheduleContext) {
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

        long now = TimeoutService.triggerTimeouts(scheduleContext);

        SortedMap<Long, List<ResultMessage<Object>>> tailView = scheduleContext.scheduled.tailMap(now);
        scheduleContext.scheduled = new TreeMap<>(tailView);

        return didSomething;
    }

    private static boolean handleScheduling(ScheduleContext scheduleContext) {
        ScheduleMessage<Object> scheduleMessage = scheduleContext.toScheduleQueue.poll();
        if (scheduleMessage != null) {
            ActionCallable<Object> actionCallable = new ActionCallable<>(scheduleMessage.action, scheduleContext
                    .toReturnQueue);
            FutureTask<Void> futureTask = new FutureTask<>(actionCallable);
            ResilientTask<Object> resilientTask = new ResilientTask<>(futureTask, scheduleMessage.promise);
            ResultMessage<Object> resultMessage = actionCallable.resultMessage;
            scheduleContext.taskMap.put(resultMessage, resilientTask);

            scheduleContext.executorService.submit(resilientTask);
            TimeoutService.scheduleTimeout(scheduleContext.scheduled, scheduleMessage.absoluteTimeout, resultMessage);
            return true;
        }
        return false;
    }

    private static boolean handleReturnResult(ScheduleContext scheduleContext) {
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

    private static void handleSyncResult(ScheduleContext scheduleContext, ResultMessage<Object>
            result) {
        if (result.result != null) {
            scheduleContext.actionMetrics.reportActionResult(Status.SUCCESS);
        } else if (result.exception instanceof ActionTimeoutException) {
            TimeoutService.scheduleTimeout(scheduleContext.scheduled, System.currentTimeMillis() - 1, result);
        } else {
            scheduleContext.actionMetrics.reportActionResult(Status.ERROR);
        }

    }

    private static void handleAsyncResult(ScheduleContext scheduleContext,
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
}
