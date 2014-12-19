package fault.scheduling;

import fault.messages.ScheduleMessage;

import java.util.concurrent.*;

/**
 * Created by timbrooks on 12/18/14.
 */
public class ThreadPoolVersion {

    private ExecutorService service = Executors.newScheduledThreadPool(10);
    private ScheduledExecutorService timeoutService = Executors.newScheduledThreadPool(1);

    public <T> void action(final ScheduleMessage<T> message) {
        final Future<Void> f = service.submit(new Callable<Void>() {
            @Override
            public Void call() {
                try {
                    T result = message.action.run();
                    message.promise.deliverResult(result);
                } catch (Exception e) {
                    message.promise.deliverError(e);
                }
                return null;
            }
        });
        timeoutService.schedule(new Runnable() {
            @Override
            public void run() {
                // TODO: Race with normal set.
                message.promise.setTimedOut();
                f.cancel(true);
            }
        }, message.relativeTimeout, TimeUnit.MILLISECONDS);

    }
}
