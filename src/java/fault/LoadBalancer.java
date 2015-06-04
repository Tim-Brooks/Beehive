package fault;

import fault.concurrent.DefaultResilientPromise;
import fault.concurrent.ResilientFuture;
import fault.concurrent.ResilientPromise;
import fault.utils.ResilientPatternAction;

/**
 * Created by timbrooks on 6/4/15.
 */
public class LoadBalancer<T, C> implements Pattern {

    @Override
    public ResilientFuture<T> submitAction(ResilientPatternAction action, long millisTimeout) {
        return submitAction(action, new DefaultResilientPromise(), null, millisTimeout);
    }

    @Override
    public ResilientFuture<T> submitAction(ResilientPatternAction action, ResilientCallback callback,
                                           long millisTimeout) {
        return null;
    }

    @Override
    public ResilientFuture<T> submitAction(ResilientPatternAction action, ResilientPromise promise,
                                           long millisTimeout) {
        return null;
    }

    @Override
    public ResilientFuture<T> submitAction(ResilientPatternAction action, ResilientPromise promise,
                                           ResilientCallback callback, long millisTimeout) {
        return null;
    }

    @Override
    public ResilientPromise<T> performAction(ResilientPatternAction action) {
        return null;
    }
}
