package fault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by timbrooks on 12/12/14.
 */
public class MultiplexingScheduler implements Scheduler {

    private Lock lock = new ReentrantLock();
    private List<ScheduleContext> servicesToSchedule = new ArrayList<>();
    private boolean running = false;

    @Override
    public void scheduleServiceExecutor(ScheduleContext scheduleContext) {
        lock.lock();
        servicesToSchedule.add(scheduleContext);
        if (!running) {
            // start
        }
        lock.unlock();

    }

    @Override
    public void unscheduleServiceExecutor(ScheduleContext scheduleContext) {
        lock.lock();
        servicesToSchedule.remove(scheduleContext);
        if (servicesToSchedule.size() == 0) {
            // shutdown
        }
        lock.unlock();
    }
}
