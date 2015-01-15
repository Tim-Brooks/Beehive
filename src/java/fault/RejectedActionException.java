package fault;

/**
 * Created by timbrooks on 1/10/15.
 */
public class RejectedActionException extends RuntimeException {

    public final RejectedReason reason;

    public RejectedActionException(RejectedReason reason) {
        this.reason = reason;
    }

}
