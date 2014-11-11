package fault.java.circuit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/5/14.
 */
public class ResilientResult<T> {
    public enum Status {SUCCESS, ERROR, PENDING, TIMED_OUT}

    public T result;
    public Throwable error;
    public AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);


    public boolean deliverResult(T result) {
        boolean swapSuccess = status.compareAndSet(Status.PENDING, Status.SUCCESS);
        if (swapSuccess) {
            this.result = result;
        }
        return swapSuccess;
    }

    public boolean deliverError(Throwable error) {
        boolean swapSuccess = status.compareAndSet(Status.PENDING, Status.SUCCESS);
        if (status.compareAndSet(Status.PENDING, Status.ERROR)) {
            this.error = error;
        }
        return swapSuccess;
    }

    public boolean setTimedOut() {
        return status.compareAndSet(Status.PENDING, Status.TIMED_OUT);
    }

    public boolean isDone() {
        return status.get() != Status.PENDING;
    }

    public boolean isError() {
        return status.get() == Status.ERROR;
    }

    public boolean isTimedOut() {
        return status.get() == Status.TIMED_OUT;
    }

    public boolean isSuccessful() {
        return status.get() == Status.SUCCESS;
    }
}
