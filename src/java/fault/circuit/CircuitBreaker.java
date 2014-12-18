package fault.circuit;

/**
 * Created by timbrooks on 11/5/14.
 */
public interface CircuitBreaker {

    public boolean isOpen();

    public boolean allowAction();

    public void informBreakerOfResult(boolean successful);

    public void setBreakerConfig(BreakerConfig breakerConfig);

    public BreakerConfig getBreakerConfig();
}
