package fault.utils;

import fault.ResilientCallback;
import fault.concurrent.ResilientPromise;

import java.util.concurrent.CountDownLatch;

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

    public static <T> ResilientCallback<T> latchedPromise(T type, final CountDownLatch latch) {
        return new ResilientCallback<T>() {
            @Override
            public void run(ResilientPromise<T> resultPromise) {
                latch.countDown();
            }
        };
    }

    public static <T> ResilientCallback<T> exceptionCallback(T type) {
        return new ResilientCallback<T>() {
            @Override
            public void run(ResilientPromise<T> resultPromise) {
                throw new RuntimeException("Boom");
            }
        };
    }
}
