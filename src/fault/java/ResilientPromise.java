package fault.java;

import java.util.concurrent.CountDownLatch;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ResilientPromise<T> {
    public T result;
    public Throwable error;
    public Status status = Status.PENDING;
    // TODO The CountDownLatch on POSIX is leading to very high await latency. Should look at blocking in FutureTask
    // TODO which does not have the same issue. There is probably some spin and park strategy work in that case.
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

    public T awaitResult() throws InterruptedException {
        latch.await();
        return result;
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

}
