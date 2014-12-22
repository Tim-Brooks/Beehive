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
    private final ExecutorService metricsService = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timeoutService = Executors.newScheduledThreadPool(1);
    private final BlockingQueue<ResilientPromise<?>> metricsQueue = new LinkedBlockingQueue<>();

    public ThreadPoolVersion(final ActionMetrics actionMetrics, final CircuitBreaker circuitBreaker) {
        metricsService.submit(new Runnable() {
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
    }

    public <T> void action(final ScheduleMessage<T> message) {
        final Future<Void> f = service.submit(new Callable<Void>() {
            @Override
            public Void call() {
                ResilientPromise<T> promise = message.promise;
                try {
                    T result = message.action.run();
                    if (promise.deliverResult(result)) {
                        metricsQueue.add(promise);
                    }
                } catch (Exception e) {
                    if (promise.deliverError(e)) {
                        metricsQueue.add(promise);
                    }
                }
                return null;
            }
        });
        timeoutService.schedule(new Runnable() {
            @Override
            public void run() {
                ResilientPromise<T> promise = message.promise;
                if (promise.setTimedOut()) {
                    f.cancel(true);
                    metricsQueue.add(promise);
                }
            }
        }, message.relativeTimeout, TimeUnit.MILLISECONDS);

    }

    public void shutdown() {
        service.shutdown();
        metricsService.shutdown();
        timeoutService.shutdown();
    }
}
