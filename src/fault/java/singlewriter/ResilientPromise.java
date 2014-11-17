package fault.java.singlewriter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ResilientPromise<T> {
    public T result;
    public Throwable error;
    private Status status = Status.PENDING;
    private CountDownLatch latch = new CountDownLatch(1);

    public void deliverResult(T result) {
        this.result = result;
        status = Status.SUCCESS;
        latch.countDown();
    }

    public void deliverError(Throwable error) {
        this.error = error;
        status = Status.ERROR;
        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
    }

    public T getResult() {
        return result;
    }

    public void setTimedOut() {
        status = Status.TIMED_OUT;
        latch.countDown();
    }


    public boolean isSuccessful() {
        return status == Status.SUCCESS;
    }

    public boolean isDone() {
        return status != Status.PENDING;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isTimedOut() {
        return status == Status.TIMED_OUT;
    }

    public enum Status {SUCCESS, ERROR, PENDING, TIMED_OUT}
}
