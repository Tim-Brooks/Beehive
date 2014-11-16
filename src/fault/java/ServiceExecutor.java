package fault.java;

import fault.java.circuit.*;

import java.util.concurrent.*;

/**
 * Created by timbrooks on 11/5/14.
 */
public class ServiceExecutor {

    private final ScheduledExecutorService threadPool;
    private final CircuitBreaker circuitBreaker;
    private final ActionMetrics actionMetrics;
    private final TimeoutService timeoutService;

    public ServiceExecutor(int poolSize) {
        this.threadPool = Executors.newScheduledThreadPool(poolSize);
        this.actionMetrics = new ActionMetrics();
        this.circuitBreaker = new CircuitBreakerImplementation(actionMetrics, new BreakerConfig());
        this.timeoutService = new TimeoutService();
    }

    public <T> ResilientResult<T> performAction(final ResilientAction<T> action, int millisTimeout) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }
        final ResilientResult<T> resilientResult = new ResilientResult<>();
        ScheduledFuture<Void> scheduledFuture = threadPool.schedule(new Callable<Void>() {
            @Override
            public Void call() {
                boolean statusSetForFirstTime = false;
                try {
                    T result = action.run();
                    statusSetForFirstTime = resilientResult.deliverResult(result);
                } catch (Exception e) {
                    statusSetForFirstTime = resilientResult.deliverError(e);
                } finally {
                    if (statusSetForFirstTime) {
                        actionMetrics.logActionResult(resilientResult);
                    }
                    circuitBreaker.informBreakerOfResult(resilientResult.isSuccessful());
                }
                return null;
            }
        }, 0, TimeUnit.MILLISECONDS);

        timeoutService.scheduleTimeout(millisTimeout, resilientResult, scheduledFuture, actionMetrics);
        return resilientResult;
    }

    public void shutdown() {
        threadPool.shutdown();
        timeoutService.shutdown();
    }
}
