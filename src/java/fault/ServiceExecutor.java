package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;

/**
 * Created by timbrooks on 12/22/14.
 */
public interface ServiceExecutor {
    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, long millisTimeout);

    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise, long millisTimeout);

    <T> ResilientPromise<T> performAction(ResilientAction<T> action);

    ActionMetrics getActionMetrics();

    CircuitBreaker getCircuitBreaker();

    void shutdown();
}
