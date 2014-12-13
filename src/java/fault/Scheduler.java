package fault;

/**
 * Created by timbrooks on 12/8/14.
 */
public interface Scheduler {

    public void scheduleServiceExecutor(ScheduleContext scheduleContext);

    public void unscheduleServiceExecutor(ScheduleContext scheduleContext);

}
