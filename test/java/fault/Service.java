package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;
import fault.utils.ServiceThreadFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by timbrooks on 6/3/15.
 */
public class Service {

    public static ServiceExecutor defaultService(String name, int poolSize, int concurrencyLevel) {
        ExecutorService service = createExecutor(name, poolSize, concurrencyLevel);
        return new BlockingExecutor(service, concurrencyLevel);
    }

    public static ServiceExecutor defaultService(String name, int poolSize, int concurrencyLevel, ActionMetrics
            metrics) {
        ExecutorService service = createExecutor(name, poolSize, concurrencyLevel);
        return new BlockingExecutor(service, concurrencyLevel, metrics);
    }

    public static ServiceExecutor defaultService(String name, int poolSize, int concurrencyLevel, ActionMetrics
            metrics, CircuitBreaker breaker) {
        ExecutorService service = createExecutor(name, poolSize, concurrencyLevel);
        return new BlockingExecutor(service, concurrencyLevel, metrics, breaker);
    }

    private static ExecutorService createExecutor(String name, int poolSize, int concurrencyLevel) {
        return new ThreadPoolExecutor(poolSize, poolSize, Long.MAX_VALUE, TimeUnit.DAYS,
                new ArrayBlockingQueue<Runnable>(concurrencyLevel * 2), new ServiceThreadFactory(name));
    }
}
