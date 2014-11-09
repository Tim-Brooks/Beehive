package fault.java;

import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.NoOpCircuitBreaker;
import fault.java.circuit.ResilientResult;

import java.util.concurrent.*;

/**
 * Created by timbrooks on 11/5/14.
 */
public class ServiceExecutor {

    private final ExecutorService threadPool;
    private final CircuitBreaker circuitBreaker;

    public ServiceExecutor(int poolSize) {
        threadPool = Executors.newFixedThreadPool(poolSize);
        circuitBreaker = new NoOpCircuitBreaker();
    }

    public <T> ResilientResult<T> performAction(final ResilientAction<T> action) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }
        Future<T> future = threadPool.submit(new Callable<T>() {
            @Override
            public T call() {
                return action.run();
            }
        });
        ResilientResult<T> result = new ResilientResult<>();
        try {
            result.deliverResult(future.get());
            result.status = ResilientResult.Status.SUCCESS;
        } catch (InterruptedException | ExecutionException e) {
            result.deliverError(e);
            result.status = ResilientResult.Status.FAILURE;
        }
        return result;
    }
}
