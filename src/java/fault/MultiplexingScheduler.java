package fault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by timbrooks on 12/12/14.
 */
public class MultiplexingScheduler implements Scheduler {

    private final Lock lock = new ReentrantLock();
    private final List<ScheduleContext> servicesToSchedule = new ArrayList<>();
    private final ScheduleLoop loop = new ScheduleLoop();
    private Thread managingThread;
    private boolean running = false;

    @Override
    public void scheduleServiceExecutor(ScheduleContext scheduleContext) {
        lock.lock();
        servicesToSchedule.add(scheduleContext);
        if (!running) {
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
            for (; ; ) {
                boolean didSomething = false;
                for (ScheduleContext context : servicesToSchedule) {
                    didSomething = loop.runLoop(context);
                }

                if (!didSomething) {
                    spinCount = 1000;
                    if (0 == --spinCount) {
                        LockSupport.parkNanos(1);
                    } else if (50 > --spinCount) {
                        Thread.yield();
                    }
                } else {
                    spinCount = maxSpin;
                }
            }
        }
    }
}
