package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;

import java.util.UUID;

/**
 * Created by timbrooks on 12/22/14.
 */
public interface ServiceExecutor {
    public static long MAX_TIMEOUT_MILLIS = 1000 * 60 * 60 * 24;

    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, long millisTimeout);

    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise, long
            millisTimeout);

    public <T> ResilientPromise<T> performAction(ResilientAction<T> action);

    public ActionMetrics getActionMetrics();

    public CircuitBreaker getCircuitBreaker();

    public UUID getExecutorUUID();

    void shutdown();
}
