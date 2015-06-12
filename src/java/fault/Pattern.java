package fault;

import fault.concurrent.ResilientFuture;
import fault.concurrent.ResilientPromise;

/**
 * Created by timbrooks on 6/4/15.
 */
public interface Pattern<C> {
    long MAX_TIMEOUT_MILLIS = 1000 * 60 * 60 * 24;

    <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, long millisTimeout);

    <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientCallback<T> callback, long millisTimeout);

    <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise, long
            millisTimeout);

    <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise,
                                        ResilientCallback<T> callback, long millisTimeout);

    <T> ResilientPromise<T> performAction(ResilientPatternAction<T, C> action);

    void shutdown();
}
