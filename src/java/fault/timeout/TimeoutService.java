package fault.timeout;

import java.util.concurrent.DelayQueue;

/**
 * Created by timbrooks on 5/30/15.
 */
public class TimeoutService {

    private final String name;
    private final DelayQueue<ActionTimeout> timeoutQueue = new DelayQueue<>();
    private final Thread timeoutThread;

    public TimeoutService(String name) {
        this.name = name;
        this.timeoutThread = initializeThread();
        this.timeoutThread.setName(name + "-timeout-thread");
    }

    public void scheduleTimeout() {

    }

    private Thread initializeThread() {
        return new Thread();
//        Thread timeouts = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                for (; ; ) {
//                    try {
//                        ActionTimeout timeout = timeoutQueue.take();
//                        @SuppressWarnings("unchecked")
//                        ResilientPromise<Object> promise = (ResilientPromise<Object>) timeout.promise;
//                        if (promise.setTimedOut()) {
//                            promise.setCompletedBy(uuid);
//                            timeout.future.cancel(true);
//                            metricsQueue.offer(Status.TIMED_OUT);
//                            circuitBreaker.informBreakerOfResult(promise.isSuccessful());
//
//                            @SuppressWarnings("unchecked")
//                            ResilientCallback<Object> callback = (ResilientCallback<Object>) timeout.callback;
//                            if (callback != null) {
//                                callback.run(promise);
//                            }
//                        } else if (!uuid.equals(promise.getCompletedBy())) {
//                            timeout.future.cancel(true);
//                            metricsQueue.offer(Status.TIMED_OUT);
//                        }
//                        semaphore.releasePermit(timeout.permit);
//
//                    } catch (InterruptedException e) {
//                        break;
//                    }
//                }
//            }
//        });

    }

}
