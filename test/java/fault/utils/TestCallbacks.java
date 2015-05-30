package fault.utils;

import fault.ResilientCallback;
import fault.concurrent.ResilientPromise;

/**
 * Created by timbrooks on 1/17/15.
 */
public class TestCallbacks {

    public static <T> ResilientCallback<T> completePromiseCallback(final ResilientPromise<ResilientPromise<T>>
                                                                           promiseToComplete) {
        return new ResilientCallback<T>() {
            @Override
            public void run(ResilientPromise<T> promise) {
                promiseToComplete.deliverResult(promise);
            }
        };
    }
}
