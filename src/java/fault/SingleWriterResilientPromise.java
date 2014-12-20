package fault;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by timbrooks on 11/16/14.
 */
public class SingleWriterResilientPromise<T> implements ResilientPromise<T> {
    private T result;

    private Throwable error;

    private volatile Status status = Status.PENDING;
    // TODO The CountDownLatch on POSIX is leading to very high await latency. Should look at blocking in FutureTask
    // TODO which does not have the same issue. There is probably some spin and park strategy work in that case.
    private CountDownLatch latch = new CountDownLatch(1);
    @Override
    public void deliverResult(T result) {
        this.result = result;
        status = Status.SUCCESS;
        latch.countDown();
    }

    @Override
    public void deliverError(Throwable error) {
        this.error = error;
        status = Status.ERROR;
        latch.countDown();
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
        return status;
    }

    @Override
    public void setTimedOut() {
        status = Status.TIMED_OUT;
        latch.countDown();
    }


    @Override
    public boolean isSuccessful() {
        return status == Status.SUCCESS;
    }

    @Override
    public boolean isDone() {
        return status != Status.PENDING;
    }

    @Override
    public boolean isError() {
        return status == Status.ERROR;
    }

    @Override
    public boolean isTimedOut() {
        return status == Status.TIMED_OUT;
    }

}
