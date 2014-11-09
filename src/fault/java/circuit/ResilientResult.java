package fault.java.circuit;

/**
 * Created by timbrooks on 11/5/14.
 */
public class ResilientResult<T> {
    public enum Status {SUCCESS, FAILURE, PENDING}

    public T result;
    public Throwable error;
    public Status status = Status.PENDING;


    public void deliverResult(T result) {
        this.result = result;
    }

    public void deliverError(Throwable error) {
        this.error = error;
    }
}
