package fault.java.singlewriter;

import fault.java.ResilientAction;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by timbrooks on 11/13/14.
 */
public class ManagingRunnable implements Runnable {

    private ConcurrentLinkedQueue<ResilientAction<?>> toScheduleQueue;
    private volatile boolean isRunning;

    public ManagingRunnable(ConcurrentLinkedQueue<ResilientAction<?>> toScheduleQueue) {
        this.toScheduleQueue = toScheduleQueue;
    }

    @Override
    public void run() {
        isRunning = true;
        while (isRunning) {
            ResilientAction<?> poll = toScheduleQueue.poll();
            if (poll != null) {

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
