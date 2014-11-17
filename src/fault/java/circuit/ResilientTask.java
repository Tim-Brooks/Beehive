package fault.java.circuit;

import fault.java.singlewriter.ResilientPromise;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/5/14.
 */
public class ResilientTask<T> implements RunnableFuture<T> {
    public enum Status {SUCCESS, ERROR, PENDING, TIMED_OUT}

    public T result;
    public Throwable error;
    public AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);
    private final FutureTask<Void> task;
    private final ResilientPromise<T> resilientPromise;

    public ResilientTask(FutureTask<Void> task, ResilientPromise<T> resilientPromise) {
        this.task = task;
        this.resilientPromise = resilientPromise;
    }

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

    @Override
    public void run() {
        task.run();

    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return task.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }

    public boolean isDone() {
        return status.get() != Status.PENDING && task.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        task.get();
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        task.get(timeout, unit);
        return result;
    }

    public boolean setTimedOut() {
        return status.compareAndSet(Status.PENDING, Status.TIMED_OUT);
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
