package fault;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 12/22/14.
 */
public class MultipleWriterResilientPromise<T> implements ResilientPromise<T> {
    private T result;
    private Throwable error;
    private AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);
    // TODO: Does this act as a memory barrier?
    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void deliverResult(T result) {
        if (status.get() == Status.PENDING) {
            if (status.compareAndSet(Status.PENDING, Status.SUCCESS)) {
                this.result = result;
            }
        }
    }

    @Override
    public void deliverError(Throwable error) {
        if (status.get() == Status.PENDING) {
            if (status.compareAndSet(Status.PENDING, Status.ERROR)) {
                this.error = error;
            }
        }
    }

    @Override
    public void await() throws InterruptedException {
        latch.await();
    }

    @Override
    public boolean await(long millis) throws InterruptedException {
        return latch.await(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public T awaitResult() throws InterruptedException {
        latch.await();
        return result;
    }

    @Override
    public T getResult() {
        return result;
    }

    @Override
    public Throwable getError() {
        return error;
    }

    @Override
    public Status getStatus() {
        return status.get();
    }

    @Override
    public void setTimedOut() {
        if (status.get() == Status.PENDING) {
            status.compareAndSet(Status.PENDING, Status.TIMED_OUT);
        }
    }

    @Override
    public boolean isSuccessful() {
        return status.get() == Status.SUCCESS;
    }

    @Override
    public boolean isDone() {
        return status.get() != Status.PENDING;
    }

    @Override
    public boolean isError() {
        return status.get() == Status.ERROR;
    }

    @Override
    public boolean isTimedOut() {
        return status.get() == Status.TIMED_OUT;
    }
}
