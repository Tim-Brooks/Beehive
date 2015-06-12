package fault.circuit;

/**
 * Created by timbrooks on 6/11/15.
 */
public class BreakerConfigBuilder {
    public int timePeriodInMillis = 1000;
    public int failureThreshold = 30;
    public int failurePercentageThreshold = 50;
    public long healthRefreshMillis = 500;
    public long timeToPauseMillis = 1000;

    public BreakerConfigBuilder timePeriodInMillis(int timePeriodInMillis) {
        this.timePeriodInMillis = timePeriodInMillis;
        return this;
    }

    public BreakerConfigBuilder failureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
        return this;
    }

    public BreakerConfigBuilder failurePercentageThreshold(int failurePercentageThreshold) {
        this.failurePercentageThreshold = failurePercentageThreshold;
        return this;
    }

    public BreakerConfigBuilder timeToPauseMillis(long timeToPauseMillis) {
        this.timeToPauseMillis = timeToPauseMillis;
        return this;
    }

    public BreakerConfigBuilder healthRefreshMillis(long healthRefreshMillis) {
        this.healthRefreshMillis = healthRefreshMillis;
        return this;
    }

    public BreakerConfig build() {
        return new BreakerConfig(failureThreshold, failurePercentageThreshold, timePeriodInMillis,
                healthRefreshMillis, timeToPauseMillis);
    }

}
