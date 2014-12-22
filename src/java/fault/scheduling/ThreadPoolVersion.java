package fault.scheduling;

import fault.ResilientPromise;
import fault.circuit.CircuitBreaker;
import fault.messages.ScheduleMessage;
import fault.metrics.ActionMetrics;

import java.util.concurrent.*;

/**
 * Created by timbrooks on 12/18/14.
 */
public class ThreadPoolVersion {

    private final ExecutorService service = Executors.newScheduledThreadPool(10);
    private final ExecutorService managingService = Executors.newFixedThreadPool(2);
    private final DelayQueue<ActionTimeout> timeoutQueue = new DelayQueue<>();
    private final BlockingQueue<ResilientPromise<?>> metricsQueue = new LinkedBlockingQueue<>();

    public ThreadPoolVersion(final ActionMetrics actionMetrics, final CircuitBreaker circuitBreaker) {
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

    public <T> void action(final ScheduleMessage<T> message) {
        final Future<Void> f = service.submit(new Callable<Void>() {
            @Override
            public Void call() {
                ResilientPromise<T> promise = message.promise;
                try {
                    T result = message.action.run();
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
        timeoutQueue.offer(new ActionTimeout(message.promise, message.relativeTimeout, f));

    }

    public void shutdown() {
        service.shutdown();
        managingService.shutdown();
    }

    private class ActionTimeout implements Delayed {

        private final ResilientPromise<?> promise;
        private final long millisRelativeTimeout;
        private final Future<Void> future;

        public ActionTimeout(ResilientPromise<?> promise, long millisRelativeTimeout, Future<Void> future) {
            this.promise = promise;
            this.millisRelativeTimeout = millisRelativeTimeout;
            this.future = future;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(millisRelativeTimeout, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(millisRelativeTimeout, o.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}
