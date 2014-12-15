package fault.scheduling;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by timbrooks on 12/12/14.
 */
public class MultiplexingScheduler implements Scheduler {

    private final Lock lock = new ReentrantLock();
    private final List<ScheduleContext> servicesToSchedule = new CopyOnWriteArrayList<>();
    private final WaitStrategy waitStrategy;
    private Thread managingThread;
    private volatile boolean running = false;

    public MultiplexingScheduler() {
        this(new AdaptiveWait());
    }

    public MultiplexingScheduler(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    @Override
    public void scheduleServiceExecutor(ScheduleContext scheduleContext) {
        lock.lock();
        servicesToSchedule.add(scheduleContext);
        if (!running) {
            running = true;
            managingThread = new Thread(new InternalScheduler(), "");
            managingThread.start();
        }
        lock.unlock();

    }

    @Override
    public void unscheduleServiceExecutor(ScheduleContext scheduleContext) {
        lock.lock();
        servicesToSchedule.remove(scheduleContext);
        if (servicesToSchedule.size() == 0) {
            managingThread.interrupt();
            managingThread = null;
            running = false;
        }
        lock.unlock();
    }

    private class InternalScheduler implements Runnable {
        private final int maxSpin = 1000;

        public void run() {
            int spinCount = maxSpin;
            while (running) {
                boolean didSomething = false;
                for (ScheduleContext context : servicesToSchedule) {
                    didSomething = ScheduleLoop.runLoop(context);
                }

                if (!didSomething) {
                    spinCount = waitStrategy.executeWait(spinCount);
                } else {
                    spinCount = maxSpin;
                }
            }
        }
    }
}
