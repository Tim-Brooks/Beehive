package fault;

/**
 * Created by timbrooks on 1/10/15.
 */
public class RejectedActionException extends RuntimeException {

    public final Reason reason;

    public RejectedActionException(Reason reason) {
        this.reason = reason;
    }

    public enum Reason {CIRCUIT_CLOSED, QUEUE_FULL;}
}
