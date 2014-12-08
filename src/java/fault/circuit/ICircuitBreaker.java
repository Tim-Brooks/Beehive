package fault.circuit;

/**
 * Created by timbrooks on 11/5/14.
 */
public interface ICircuitBreaker {

    public boolean isOpen();

    boolean allowAction();

    void informBreakerOfResult(boolean successful);

    void setBreakerConfig(BreakerConfig breakerConfig);
}
