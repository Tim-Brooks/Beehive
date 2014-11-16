package fault.java;

import fault.java.circuit.ResilientResult;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by timbrooks on 11/8/14.
 */
public class TimeoutService {

    private final ScheduledExecutorService executorService;

    public TimeoutService() {
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public <T> void scheduleTimeout(int millisTimeout, final ResilientResult<T> resilientResult, final ScheduledFuture<Void> scheduledAction, final ActionMetrics actionMetrics) {
        executorService.schedule(new Runnable() {
            @Override
            public void run() {
                scheduledAction.cancel(true);
                if (resilientResult.setTimedOut()) {
                    actionMetrics.logActionResult(resilientResult);
                }
            }
        }, millisTimeout, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        executorService.shutdown();
    }
}
