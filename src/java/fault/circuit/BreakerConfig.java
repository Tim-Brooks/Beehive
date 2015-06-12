package fault.circuit;

/**
 * Created by timbrooks on 11/10/14.
 */
public class BreakerConfig {

    public final int timePeriodInMillis;
    public final int failurePercentageThreshold;
    public final int failureThreshold;
    private final long healthRefreshMillis;
    public final long timeToPauseMillis;

    public BreakerConfig(int failureThreshold, int failurePercentageThreshold, int timePeriodInMillis,
                         long healthRefreshMillis, long timeToPauseMillis) {
        this.failureThreshold = failureThreshold;
        this.failurePercentageThreshold = failurePercentageThreshold;
        this.timePeriodInMillis = timePeriodInMillis;
        this.healthRefreshMillis = healthRefreshMillis;
        this.timeToPauseMillis = timeToPauseMillis;
    }

}
