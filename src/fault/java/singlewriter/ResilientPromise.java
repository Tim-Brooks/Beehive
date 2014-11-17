package fault.java.singlewriter;

import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ResilientPromise<T> {
    public enum Status {SUCCESS, ERROR, PENDING, TIMED_OUT}

    public T result;
    public Throwable error;
    public AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);


    public boolean isSuccessful() {
        return true;
    }
}
