package fault.java;

/**
 * Created by timbrooks on 11/20/14.
 */
public class ActionTimeoutException extends Exception {

    public ActionTimeoutException(String message) {
        super(message);
    }

    public ActionTimeoutException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
