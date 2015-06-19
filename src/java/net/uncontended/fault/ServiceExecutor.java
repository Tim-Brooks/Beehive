package net.uncontended.fault;

import net.uncontended.fault.circuit.CircuitBreaker;
import net.uncontended.fault.concurrent.ResilientFuture;
import net.uncontended.fault.concurrent.ResilientPromise;
import net.uncontended.fault.metrics.ActionMetrics;

/**
 * Created by timbrooks on 12/22/14.
 */
public interface ServiceExecutor {
    long MAX_TIMEOUT_MILLIS = 1000 * 60 * 60 * 24;

    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, long millisTimeout);

    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientCallback<T> callback, long
            millisTimeout);

    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise, long
            millisTimeout);

    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise,
                                        ResilientCallback<T> callback, long millisTimeout);

    <T> ResilientPromise<T> performAction(ResilientAction<T> action);

    ActionMetrics getActionMetrics();

    CircuitBreaker getCircuitBreaker();

    void shutdown();
}
