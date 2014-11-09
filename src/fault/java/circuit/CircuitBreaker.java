package fault.java.circuit;

/**
 * Created by timbrooks on 11/5/14.
 */
public interface CircuitBreaker {

    public boolean isOpen();

    void informBreakerOfResult(boolean successful);
}
