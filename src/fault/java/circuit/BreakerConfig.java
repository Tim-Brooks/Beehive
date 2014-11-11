package fault.java.circuit;

/**
 * Created by timbrooks on 11/10/14.
 */
public class BreakerConfig {

    public final int timePeriodInMillis;
    public final int failureThreshold;

    public BreakerConfig() {
        this.failureThreshold = 20;
        this.timePeriodInMillis = 5000;
    }
}
