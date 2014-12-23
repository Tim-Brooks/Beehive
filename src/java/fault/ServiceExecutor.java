package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;

/**
 * Created by timbrooks on 12/22/14.
 */
public interface ServiceExecutor {
    <T> ResilientPromise<T> performAction(ResilientAction<T> action, int millisTimeout);

    <T> ResilientPromise<T> performSyncAction(ResilientAction<T> action);

    ActionMetrics getActionMetrics();

    CircuitBreaker getCircuitBreaker();

    void shutdown();
}
