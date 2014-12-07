package fault.circuit;

/**
 * Created by timbrooks on 11/10/14.
 */
public class BreakerConfig {

    public final int timePeriodInMillis;
    public final int failureThreshold;

    public BreakerConfig(int failureThreshold, int timePeriodInMillis) {
        this.failureThreshold = failureThreshold;
        this.timePeriodInMillis = timePeriodInMillis;
    }

    public static class BreakerConfigBuilder {
        public int timePeriodInMillis;
        public int failureThreshold;

        public BreakerConfigBuilder timePeriodInMillis(int timePeriodInMillis) {
            this.timePeriodInMillis = timePeriodInMillis;
            return this;
        }

        public BreakerConfigBuilder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        public BreakerConfig build() {
            return new BreakerConfig(failureThreshold, timePeriodInMillis);
        }

    }
}
