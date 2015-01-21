package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;
import org.httpkit.client.HttpClient;

import java.util.concurrent.*;

/**
 * Created by timbrooks on 1/19/15.
 */
public class HttpKitExecutor extends AbstractServiceExecutor {

    public final ThreadPoolExecutor callbackExecutor;
    public final HttpClient client;
    private final Semaphore semaphore;
    private final BlockingQueue<Enum<?>> metricsQueue = new LinkedBlockingQueue<>();
    private final String name;


    public HttpKitExecutor(int concurrencyLevel, String name, CircuitBreaker circuitBreaker, ActionMetrics
            actionMetrics, HttpClient client) {
        super(circuitBreaker, actionMetrics);
        this.client = client;
        this.semaphore = new Semaphore(concurrencyLevel);

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
        ResilientPromise<T> promise = null;
        return submitAction(action, promise, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientCallback<T> callback, long
            millisTimeout) {
        return submitAction(action, null, callback, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise, long
            millisTimeout) {
        return submitAction(action, promise, null, millisTimeout);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise,
                                               ResilientCallback<T> callback, long millisTimeout) {
        Semaphore.Permit permit = acquirePermitOrRejectIfActionNotAllowed();
        try {
            action.run();
        } catch (Exception e) {

        }
        semaphore.releasePermit(permit);

        return null;
    }

    @Override
    public <T> ResilientPromise<T> performAction(ResilientAction<T> action) {
        return null;
    }

    @Override
    public void shutdown() {

    }

    private Semaphore.Permit acquirePermitOrRejectIfActionNotAllowed() {
        Semaphore.Permit permit = semaphore.acquirePermit();
        if (permit == null) {
            metricsQueue.add(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
            throw new RejectedActionException(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
        }
        if (!circuitBreaker.allowAction()) {
            metricsQueue.add(RejectionReason.CIRCUIT_OPEN);
            semaphore.releasePermit(permit);
            throw new RejectedActionException(RejectionReason.CIRCUIT_OPEN);
        }
        return permit;
    }
}
