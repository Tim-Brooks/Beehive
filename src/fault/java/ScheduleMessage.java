package fault.java;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ScheduleMessage<T> {

    public final ResilientAction<T> action;
    public final ResilientPromise<T> promise;
    public final long relativeTimeout;

    public ScheduleMessage(ResilientAction<T> action, ResilientPromise<T> promise, long relativeTimeout) {
        this.action = action;
        this.promise = promise;
        this.relativeTimeout = relativeTimeout;
    }
}
