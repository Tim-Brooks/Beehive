package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by timbrooks on 1/19/15.
 */
public class HttpkitExecutor extends AbstractServiceExecutor {

    private final ThreadPoolExecutor callbackExecutor;
    private final String name;


    public HttpkitExecutor(int concurrencyLevel, String name, CircuitBreaker circuitBreaker, ActionMetrics
            actionMetrics) {
        super(circuitBreaker, actionMetrics);

        if (name == null) {
            this.name = this.toString();
        } else {
            this.name = name;
        }
        int poolSize = Runtime.getRuntime().availableProcessors();
        callbackExecutor = new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS, new
                ArrayBlockingQueue<Runnable>
                (concurrencyLevel * 2), new ServiceThreadFactory(name));
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, long millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientCallback<T> callback, long
            millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise, long
            millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise,
                                               ResilientCallback<T> callback, long millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientPromise<T> performAction(ResilientAction<T> action) {
        return null;
    }

    @Override
    public void shutdown() {

    }
}
