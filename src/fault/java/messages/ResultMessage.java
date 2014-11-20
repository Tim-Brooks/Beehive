package fault.java.messages;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ResultMessage<T> {

    public T result;
    public Throwable exception;

    public void setResult(T result) {
        this.result = result;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }
}
