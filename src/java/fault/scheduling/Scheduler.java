package fault.scheduling;

import fault.scheduling.ScheduleContext;

/**
 * Created by timbrooks on 12/8/14.
 */
public interface Scheduler {

    public static Scheduler defaultScheduler = new MultiplexingScheduler();

    public void scheduleServiceExecutor(ScheduleContext scheduleContext);

    public void unscheduleServiceExecutor(ScheduleContext scheduleContext);

}
