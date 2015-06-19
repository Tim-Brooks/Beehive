package net.uncontended.fault;

import net.uncontended.fault.concurrent.ResilientFuture;
import net.uncontended.fault.concurrent.ResilientPromise;

/**
 * Created by timbrooks on 6/4/15.
 */
public interface Pattern<C> {
    /**
     * Submits a {@link ResilientPatternAction} that will be ran asynchronously.
     * The result of the action will be delivered to the future returned
     * by this call. An attempt to cancel the action will be made if it
     * does not complete before the timeout.
     *
     * @param action        the action to submit
     * @param millisTimeout milliseconds before the action times out
     * @return a {@link ResilientFuture} representing pending completion of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, long millisTimeout);

    /**
     * Submits a {@link ResilientPatternAction} that will be ran asynchronously similar to
     * {@link #submitAction(ResilientPatternAction, long)}. However, at the completion of the
     * task, the provided callback will be executed. The callback will be run regardless of
     * the result of the action.
     *
     * @param action        the action to submit
     * @param callback      to run on action completion
     * @param millisTimeout milliseconds before the action times out
     * @return a {@link ResilientFuture} representing pending completion of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientCallback<T> callback, long millisTimeout);

    /**
     * Submits a {@link ResilientPatternAction} that will be ran asynchronously similar to
     * {@link #submitAction(ResilientPatternAction, long)}. However, at the completion of
     * the task, the result will be delivered to the promise provided.
     *
     * @param action        the action to submit
     * @param promise       a promise to which deliver the result
     * @param millisTimeout milliseconds before the action times out
     * @return a {@link ResilientFuture} representing pending completion of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise, long
            millisTimeout);

    /**
     * Submits a {@link ResilientPatternAction} that will be ran asynchronously similar to
     * {@link #submitAction(ResilientPatternAction, long)}. However, at the completion
     * of the task, the result will be delivered to the promise provided. And the provided
     * callback will be executed.
     *
     * @param action        the action to submit
     * @param promise       a promise to which deliver the result
     * @param callback      to run on action completion
     * @param millisTimeout milliseconds before the action times out
     * @return a {@link ResilientFuture} representing pending completion of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise,
                                        ResilientCallback<T> callback, long millisTimeout);

    /**
     * Performs a {@link ResilientPatternAction} that will be ran synchronously on the
     * calling thread. However, at the completion of the task, the result will be delivered
     * to the promise provided. And the provided callback will be executed.
     * <p/>
     * <p/>
     * If the ResilientPatternAction throws a {@link ActionTimeoutException}, the result
     * of the action will be a timeout. Any other exception and the result of the action
     * will be an error.
     *
     * @param action the action to run
     * @return a {@link ResilientPromise} representing result of the action
     * @throws RejectedActionException if the action is rejected
     */
    <T> ResilientPromise<T> performAction(ResilientPatternAction<T, C> action);

    /**
     * Attempts to shutdown the service. Calls made to submitAction or performAction
     * after this call will throw a {@link RejectedActionException}. Implementations
     * may differ on if pending or executing actions are cancelled.
     */
    void shutdown();
}
