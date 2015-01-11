package fault;

import fault.circuit.BreakerConfig;
import fault.circuit.CircuitBreaker;
import fault.circuit.DefaultCircuitBreaker;
import fault.metrics.ActionMetrics;
import fault.metrics.SingleWriterActionMetrics;

import java.util.concurrent.*;

/**
 * Created by timbrooks on 12/23/14.
 */
public class BlockingExecutor extends AbstractServiceExecutor implements ServiceExecutor {

    private final ExecutorService service;
    private final ExecutorService managingService = Executors.newFixedThreadPool(2);
    private final DelayQueue<ActionTimeout> timeoutQueue = new DelayQueue<>();
    private final BlockingQueue<ResilientPromise<?>> metricsQueue = new LinkedBlockingQueue<>();

    public BlockingExecutor(int poolSize) {
        this(poolSize, new SingleWriterActionMetrics(3600));
    }

    public BlockingExecutor(int poolSize, ActionMetrics actionMetrics) {
        this(poolSize, actionMetrics, new DefaultCircuitBreaker(actionMetrics, new BreakerConfig.BreakerConfigBuilder
                ().failureThreshold(20).timePeriodInMillis(5000).build()));
    }

    public BlockingExecutor(int poolSize, ActionMetrics actionMetrics, CircuitBreaker circuitBreaker) {
        super(circuitBreaker, actionMetrics);
        this.service = Executors.newFixedThreadPool(poolSize);
        startTimeoutAndMetrics();
    }

    @Override
    public <T> ResilientFuture<T> performAction(final ResilientAction<T> action, long millisTimeout) {
        return performAction(action, new MultipleWriterResilientPromise<T>(), millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> performAction(final ResilientAction<T> action, final ResilientPromise<T> promise,
                                                long millisTimeout) {
        if (!circuitBreaker.allowAction()) {
            throw new RuntimeException("Circuit is Open");
        }
        final Future<Void> f = service.submit(new Callable<Void>() {
            @Override
            public Void call() {
                try {
                    T result = action.run();
                    if (promise.deliverResult(result)) {
                        metricsQueue.offer(promise);
                    }
                } catch (Exception e) {
                    if (promise.deliverError(e)) {
                        metricsQueue.offer(promise);
                    }
                }
                return null;
            }
        });
        timeoutQueue.offer(new ActionTimeout(promise, millisTimeout, f));
        return new ResilientFuture<>(promise);
    }

    @Override
    public <T> ResilientPromise<T> performSyncAction(final ResilientAction<T> action) {
        ResilientPromise<T> promise = new SingleWriterResilientPromise<>();
        if (!circuitBreaker.allowAction()) {
            throw new RuntimeException("Circuit is Open");
        }
        try {
            T result = action.run();
            promise.deliverResult(result);
        } catch (ActionTimeoutException e) {
            promise.setTimedOut();
        } catch (Exception e) {
            promise.deliverError(e);
        }
        metricsQueue.offer(promise);
        return promise;
    }

    @Override
    public void shutdown() {
        service.shutdown();
        managingService.shutdown();
        managingService.shutdown();
    }

    private void startTimeoutAndMetrics() {
        managingService.submit(new Runnable() {
            @Override
            public void run() {
                for (; ; ) {
                    try {
                        ResilientPromise<?> promise = metricsQueue.take();
                        actionMetrics.reportActionResult(promise.getStatus());
                        circuitBreaker.informBreakerOfResult(promise.isSuccessful());
                    } catch (InterruptedException e) {
                        break;
                    }

                }
            }
        });

        managingService.submit(new Runnable() {
            @Override
            public void run() {
                for (; ; ) {
                    try {
                        ActionTimeout timeout = timeoutQueue.take();
                        ResilientPromise<?> promise = timeout.promise;
                        if (promise.setTimedOut()) {
                            timeout.future.cancel(true);
                            actionMetrics.reportActionResult(promise.getStatus());
                            circuitBreaker.informBreakerOfResult(promise.isSuccessful());
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
    }

    private class ActionTimeout implements Delayed {

        private final ResilientPromise<?> promise;
        private final long millisAbsoluteTimeout;
        private final Future<Void> future;

        public ActionTimeout(ResilientPromise<?> promise, long millisRelativeTimeout, Future<Void> future) {
            this.promise = promise;
            this.millisAbsoluteTimeout = millisRelativeTimeout + System.currentTimeMillis();
            this.future = future;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(millisAbsoluteTimeout - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(millisAbsoluteTimeout, o.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}
