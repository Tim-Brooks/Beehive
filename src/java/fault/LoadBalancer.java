package fault;

import fault.concurrent.DefaultResilientPromise;
import fault.concurrent.ResilientFuture;
import fault.concurrent.ResilientPromise;
import fault.utils.ResilientPatternAction;

/**
 * Created by timbrooks on 6/4/15.
 */
public class LoadBalancer<C> implements Pattern<C> {

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, long millisTimeout) {
        return submitAction(action, new DefaultResilientPromise<T>(), null, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientCallback<T> callback,
                                               long millisTimeout) {
        return submitAction(action, new DefaultResilientPromise<T>(), callback, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise,
                                               long millisTimeout) {
        return submitAction(action, promise, null, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise,
                                               ResilientCallback<T> callback, long millisTimeout) {
        return submitAction(action, promise, callback, millisTimeout);
    }

    @Override
    public <T> ResilientPromise<T> performAction(ResilientPatternAction<T, C> action) {
        return null;
    }
}
