package fault.java.singlewriter;

import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by timbrooks on 11/13/14.
 */
public class ManagingRunnable implements Runnable {

    private final ConcurrentLinkedQueue<Runnable> toScheduleQueue;
    private final ConcurrentLinkedQueue<?> toReturnQueue;
    private final ScheduledExecutorService executorService;
    private volatile boolean isRunning;

    public ManagingRunnable(int poolSize, ConcurrentLinkedQueue<Runnable> toScheduleQueue, ConcurrentLinkedQueue<?> toReturnQueue) {
        this.toScheduleQueue = toScheduleQueue;
        this.toReturnQueue = toReturnQueue;
        executorService = Executors.newScheduledThreadPool(poolSize);
    }

    @Override
    public void run() {
        isRunning = true;
        while (isRunning) {
            Runnable poll = toScheduleQueue.poll();
            if (poll != null) {
                ScheduledFuture<?> future = executorService.schedule(poll, 0, TimeUnit.MILLISECONDS);
            } else {
                // This will change for sure.
                LockSupport.parkNanos(1);
            }

        }

    }

    public void shutdown() {
        isRunning = false;
    }
}
