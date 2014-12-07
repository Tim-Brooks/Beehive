package fault.circuit;

/**
 * Created by timbrooks on 11/5/14.
 */
public class NoOpCircuitBreaker implements ICircuitBreaker {
    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void informBreakerOfResult(boolean successful) {
    }

    @Override
    public void setBreakerConfig(BreakerConfig breakerConfig) {

    }
}
