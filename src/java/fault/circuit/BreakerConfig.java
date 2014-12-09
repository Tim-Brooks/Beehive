package fault.circuit;

/**
 * Created by timbrooks on 11/10/14.
 */
public class BreakerConfig {

    public final int timePeriodInMillis;
    public final int failureThreshold;
    public final long timeToPauseMillis;

    public BreakerConfig(int failureThreshold, int timePeriodInMillis, long timeToPauseMillis) {
        this.failureThreshold = failureThreshold;
        this.timePeriodInMillis = timePeriodInMillis;
        this.timeToPauseMillis = timeToPauseMillis;
    }

    public static class BreakerConfigBuilder {
        public int timePeriodInMillis = 1000;
        public int failureThreshold = 20;
        public long timeToPauseMillis = 1000;

        public BreakerConfigBuilder timePeriodInMillis(int timePeriodInMillis) {
            this.timePeriodInMillis = timePeriodInMillis;
            return this;
        }

        public BreakerConfigBuilder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        public BreakerConfigBuilder timeToPauseMillis(long timeToPauseMillis) {
            this.timeToPauseMillis = timeToPauseMillis;
            return this;
        }

        public BreakerConfig build() {
            return new BreakerConfig(failureThreshold, timePeriodInMillis, timeToPauseMillis);
        }

    }
}
