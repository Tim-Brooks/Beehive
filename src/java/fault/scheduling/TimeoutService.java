package fault.scheduling;

import fault.concurrent.ResilientPromise;
import fault.Status;
import fault.messages.ResultMessage;

import java.util.*;

/**
 * Created by timbrooks on 12/13/14.
 */
public class TimeoutService {

    public static void scheduleTimeout(SortedMap<Long, List<ResultMessage<Object>>> scheduled, long absoluteTimeout,
                                       ResultMessage<Object> resultMessage) {
        if (scheduled.containsKey(absoluteTimeout)) {
            scheduled.get(absoluteTimeout).add(resultMessage);
        } else {
            List<ResultMessage<Object>> messages = new ArrayList<>();
            messages.add(resultMessage);
            scheduled.put(absoluteTimeout, messages);

        }
    }

    public static void triggerTimeouts(ScheduleContext scheduleContext) {
        long now = scheduleContext.systemTime.currentTimeMillis();
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

        SortedMap<Long, List<ResultMessage<Object>>> tailView = scheduleContext.scheduled.tailMap(now);
        scheduleContext.scheduled = new TreeMap<>(tailView);
    }

    public static void handleSyncTimeout(ScheduleContext scheduleContext) {
        scheduleContext.actionMetrics.reportActionResult(Status.TIMEOUT);
        scheduleContext.circuitBreaker.informBreakerOfResult(false);
    }

    public static void handleAsyncTimeout(ScheduleContext scheduleContext, ResultMessage<Object>
            resultMessage) {
        ResilientTask<Object> task = scheduleContext.taskMap.remove(resultMessage);
        if (task != null) {
            ResilientPromise<Object> promise = task.resilientPromise;
            if (!promise.isDone()) {
                promise.setTimedOut();
                task.cancel(true);
                scheduleContext.actionMetrics.reportActionResult(promise.getStatus());
                scheduleContext.circuitBreaker.informBreakerOfResult(false);
            }
        }
    }
}
