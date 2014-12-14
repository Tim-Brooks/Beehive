package fault;

import fault.messages.ResultMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

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

    public static long triggerTimeouts(ScheduleContext scheduleContext) {
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

    private static void handleSyncTimeout(ScheduleContext scheduleContext) {
        scheduleContext.actionMetrics.reportActionResult(Status.TIMED_OUT);
        scheduleContext.circuitBreaker.informBreakerOfResult(false);
    }

    private static void handleAsyncTimeout(ScheduleContext scheduleContext, ResultMessage<Object>
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
