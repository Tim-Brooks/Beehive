package fault;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/16/14.
 */
public class SingleWriterResilientPromise<T> implements ResilientPromise<T> {
    private T result;
    private Throwable error;
    private AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);
    // TODO: Does this act as a memory barrier?
    private CountDownLatch latch = new CountDownLatch(1);

    @Override
    public boolean deliverResult(T result) {
        this.result = result;
        status.lazySet(Status.SUCCESS);
        latch.countDown();
        return true;
    }

    @Override
    public boolean deliverError(Throwable error) {
        this.error = error;
        status.lazySet(Status.ERROR);
        latch.countDown();
        return true;
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
    public boolean setTimedOut() {
        status.lazySet(Status.TIMED_OUT);
        latch.countDown();
        return true;
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
