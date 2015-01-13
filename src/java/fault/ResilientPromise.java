package fault;

import java.util.UUID;

/**
 * Created by timbrooks on 12/19/14.
 */
public interface ResilientPromise<T> {
    public boolean deliverResult(T result);

    public boolean deliverError(Throwable error);

    public void await() throws InterruptedException;

    public boolean await(long millis) throws InterruptedException;

    public T awaitResult() throws InterruptedException;

    public T getResult();

    public Throwable getError();

    public Status getStatus();

    public boolean setTimedOut();

    public boolean isSuccessful();

    public boolean isDone();

    public boolean isError();

    public boolean isTimedOut();

    public UUID getCompletedBy();

    public void setCompletedBy(UUID executorUUID);
}
