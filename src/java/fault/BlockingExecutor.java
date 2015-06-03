package fault;

import fault.circuit.BreakerConfig;
import fault.circuit.CircuitBreaker;
import fault.circuit.DefaultCircuitBreaker;
import fault.concurrent.*;
import fault.metrics.ActionMetrics;
import fault.metrics.Metric;
import fault.metrics.DefaultActionMetrics;
import fault.timeout.ActionTimeout;
import fault.timeout.TimeoutService;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by timbrooks on 12/23/14.
 */
public class BlockingExecutor extends AbstractServiceExecutor {

    private static final int MAX_CONCURRENCY_LEVEL = Integer.MAX_VALUE / 2;
    private final ExecutorService service;
    private final TimeoutService timeoutService = TimeoutService.defaultTimeoutService;
    private final String name;
    private final ExecutorSemaphore semaphore;

    public BlockingExecutor(int poolSize, int concurrencyLevel) {
        this(poolSize, concurrencyLevel, null);
    }

    public BlockingExecutor(int poolSize, int concurrencyLevel, String name) {
        this(poolSize, concurrencyLevel, name, new DefaultActionMetrics(3600));
    }

    public BlockingExecutor(int poolSize, int concurrencyLevel, String name, ActionMetrics actionMetrics) {
        this(poolSize, concurrencyLevel, name, actionMetrics, new DefaultCircuitBreaker(actionMetrics, new BreakerConfig
                .BreakerConfigBuilder().failureThreshold(20).timePeriodInMillis(5000).build()));
    }

    public BlockingExecutor(int poolSize, int concurrencyLevel, String name, CircuitBreaker breaker) {
        this(poolSize, concurrencyLevel, name, new DefaultActionMetrics(3600), breaker);
    }

    public BlockingExecutor(int poolSize, int concurrencyLevel, String name, ActionMetrics actionMetrics, CircuitBreaker
            circuitBreaker) {
        super(circuitBreaker, actionMetrics);
        if (concurrencyLevel > MAX_CONCURRENCY_LEVEL) {
            throw new RuntimeException("Concurrency Level \"" + concurrencyLevel + "\" is greater than the allowed " +
                    "maximum: " + MAX_CONCURRENCY_LEVEL + ".");
        }

        if (name == null) {
            this.name = this.toString();
        } else {
            this.name = name;
        }
        this.semaphore = new ExecutorSemaphore(concurrencyLevel);
        this.service = new ThreadPoolExecutor(poolSize, poolSize, Long.MAX_VALUE, TimeUnit.DAYS,
                new ArrayBlockingQueue<Runnable>(concurrencyLevel * 2), new ServiceThreadFactory());
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, long millisTimeout) {
        return submitAction(action, (ResilientPromise<T>) null, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientPromise<T> promise, long
            millisTimeout) {
        return submitAction(action, promise, null, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, ResilientCallback<T> callback, long
            millisTimeout) {
        return submitAction(action, null, callback, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(final ResilientAction<T> action, final ResilientPromise<T> promise,
                                               final ResilientCallback<T> callback, long millisTimeout) {
        acquirePermitOrRejectIfActionNotAllowed();
        final AbstractResilientPromise<T> internalPromise = new MultipleWriterResilientPromise<>();
        if (promise != null) {
            internalPromise.wrapPromise(promise);
        }
        try {
            final Future<Void> f = service.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        T result = action.run();
                        internalPromise.deliverResult(result);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    } catch (Exception e) {
                        internalPromise.deliverError(e);
                    } finally {
                        actionMetrics.incrementMetric(Metric.statusToMetric(internalPromise.getStatus()));
                        circuitBreaker.informBreakerOfResult(internalPromise.isSuccessful());
                        if (callback != null) {
                            callback.run(promise == null ? internalPromise : promise);
                        }

                        semaphore.releasePermit();
                    }
                    return null;
                }
            });

            if (millisTimeout > MAX_TIMEOUT_MILLIS) {
                timeoutService.scheduleTimeout(new ActionTimeout(MAX_TIMEOUT_MILLIS, internalPromise, f));
            } else {
                timeoutService.scheduleTimeout(new ActionTimeout(millisTimeout, internalPromise, f));
            }
        } catch (RejectedExecutionException e) {
            actionMetrics.incrementMetric(Metric.QUEUE_FULL);
            semaphore.releasePermit();
            throw new RejectedActionException(RejectionReason.QUEUE_FULL);
        }

        if (promise != null) {
            return new ResilientFuture<>(promise);
        } else {
            return new ResilientFuture<>(internalPromise);
        }

    }

    @Override
    public <T> ResilientPromise<T> performAction(final ResilientAction<T> action) {
        ResilientPromise<T> promise = new SingleWriterResilientPromise<>();
        acquirePermitOrRejectIfActionNotAllowed();
        try {
            T result = action.run();
            promise.deliverResult(result);
        } catch (ActionTimeoutException e) {
            promise.setTimedOut();
        } catch (Exception e) {
            promise.deliverError(e);
        }

        actionMetrics.incrementMetric(Metric.statusToMetric(promise.getStatus()));
        semaphore.releasePermit();

        return promise;
    }

    @Override
    public void shutdown() {
        // Need to shutdown metrics and timeout

        service.shutdown();
    }

    private void acquirePermitOrRejectIfActionNotAllowed() {
        boolean isPermitAcquired = semaphore.acquirePermit();
        if (!isPermitAcquired) {
            actionMetrics.incrementMetric(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED);
            throw new RejectedActionException(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
        }
        if (!circuitBreaker.allowAction()) {
            actionMetrics.incrementMetric(Metric.CIRCUIT_OPEN);
            semaphore.releasePermit();
            throw new RejectedActionException(RejectionReason.CIRCUIT_OPEN);
        }
    }

    private class ServiceThreadFactory implements ThreadFactory {

        public final AtomicInteger count = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, name + "-" + count.getAndIncrement());
        }
    }
}
