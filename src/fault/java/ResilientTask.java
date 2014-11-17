package fault.java;

import fault.java.singlewriter.ResilientPromise;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by timbrooks on 11/5/14.
 */
public class ResilientTask<T> implements RunnableFuture<Void> {

    public final ResilientPromise<T> resilientPromise;
    private final FutureTask<Void> task;

    public ResilientTask(FutureTask<Void> task, ResilientPromise<T> resilientPromise) {
        this.task = task;
        this.resilientPromise = resilientPromise;
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
        return task.isDone();
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        return task.get();
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return task.get(timeout, unit);
    }
}
