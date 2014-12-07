package fault.messages;

import fault.ResilientAction;
import fault.ResilientPromise;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ScheduleMessage<T> {

    public final ResilientAction<T> action;
    public final ResilientPromise<T> promise;
    public final long absoluteTimeout;

    public ScheduleMessage(ResilientAction<T> action, ResilientPromise<T> promise, long absoluteTimeout) {
        this.action = action;
        this.promise = promise;
        this.absoluteTimeout = absoluteTimeout;
    }
}
