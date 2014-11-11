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
    private AtomicBoolean reportedStatus = new AtomicBoolean(false);


    public void deliverResult(T result) {
        if (status.compareAndSet(Status.PENDING, Status.SUCCESS)) {
            this.result = result;
        }
    }

    public void deliverError(Throwable error) {
        if (status.compareAndSet(Status.PENDING, Status.ERROR)) {
            this.error = error;
        }
    }

    public void setTimedOut() {
        status.compareAndSet(Status.PENDING, Status.TIMED_OUT);
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

    public boolean shouldReportStatus() {
        return reportedStatus.compareAndSet(false, true);

    }
}
