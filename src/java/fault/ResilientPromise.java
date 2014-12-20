package fault;

/**
 * Created by timbrooks on 12/19/14.
 */
public interface ResilientPromise<T> {
    void deliverResult(T result);

    void deliverError(Throwable error);

    void await() throws InterruptedException;

    boolean await(long millis) throws InterruptedException;

    T awaitResult() throws InterruptedException;

    T getResult();

    Throwable getError();

    Status getStatus();

    void setTimedOut();

    boolean isSuccessful();

    boolean isDone();

    boolean isError();

    boolean isTimedOut();
}
