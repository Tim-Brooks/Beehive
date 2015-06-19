package net.uncontended.fault;

import net.uncontended.fault.circuit.CircuitBreaker;
import net.uncontended.fault.concurrent.ResilientFuture;
import net.uncontended.fault.concurrent.ResilientPromise;
import net.uncontended.fault.metrics.ActionMetrics;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Created by timbrooks on 12/22/14.
 */
public interface ServiceExecutor {
    long MAX_TIMEOUT_MILLIS = 1000 * 60 * 60 * 24;

    /**
     * Submits a {@link ResilientAction} that will be ran asynchronously.
     * The result of the action will be delivered to the future returned
     * by this call. An attempt to cancel the action will be made if it
     * does not complete before the timeout.
     *
     * @param action the action to submit
     * @param millisTimeout milliseconds before the action times out
     * @return a {@link ResilientFuture} representing pending completion of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, long millisTimeout);

    /**
     * Submits a {@link ResilientAction} that will be ran asynchronously
     * similar to {@link #submitAction(ResilientAction, long)}. However, at the
     * completion of the task, the provided callback will be executed.
     *
     * @param action the action to submit
     * @param millisTimeout milliseconds before the action times out
     * @return a {@link ResilientFuture} representing pending completion of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientCallback<T> callback, long
            millisTimeout);

    /**
     * Submits a {@link ResilientAction} that will be ran asynchronously
     * similar to {@link #submitAction(ResilientAction, long)}. However, at the
     * completion of the task, the result will be delivered to the promise provided.
     *
     * @param action the action to submit
     * @param millisTimeout milliseconds before the action times out
     * @return a {@link ResilientFuture} representing pending completion of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise, long
            millisTimeout);

    /**
     * Submits a {@link ResilientAction} that will be ran asynchronously
     * similar to {@link #submitAction(ResilientAction, long)}. However, at the completion
     * of the task, the result will be delivered to the promise provided. And the provided
     * callback will be executed.
     *
     * @param action the action to submit
     * @param millisTimeout milliseconds before the action times out
     * @return a {@link ResilientFuture} representing pending completion of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise,
                                        ResilientCallback<T> callback, long millisTimeout);

    /**
     * Performs a {@link ResilientAction} that will be ran synchronously on the calling
     * thread. However, at the completion of the task, the result will be delivered to
     * the promise provided. And the provided callback will be executed.
     *
     * <p>
     * If the ResilientAction throws a {@link ActionTimeoutException}, the result of
     * the action will be a timeout. Any other exception and the result of the action
     * will be a error.
     *
     * @param action the action to run
     * @return a {@link ResilientPromise} representing result of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientPromise<T> performAction(ResilientAction<T> action);

    /**
     * Returns the {@link ActionMetrics} for this service.
     *
     * @return the metrics backing this service
     */
    ActionMetrics getActionMetrics();

    /**
     * Returns the {@link CircuitBreaker} for this service.
     *
     * @return the circuit breaker for this service
     */
    CircuitBreaker getCircuitBreaker();

    /**
     * Attempts to shutdown the service. Calls made to submitAction or performAction
     * after this call will throw a {@link RejectedActionException}. Implementations
     * may different on if pending or executing actions are cancelled.
     *
     */
    void shutdown();
}
