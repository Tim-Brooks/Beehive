package fault.metrics;

/**
 * Created by timbrooks on 6/3/15.
 */
public interface NewActionMetrics {
    void incrementMetric(Metric metric);

    int getMetricForTimePeriod(Metric metric, long milliseconds);
}
