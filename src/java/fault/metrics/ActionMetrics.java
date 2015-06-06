package fault.metrics;

/**
 * Created by timbrooks on 6/3/15.
 */
public interface ActionMetrics {
    void incrementMetricCount(Metric metric);

    int getMetricCountForTimePeriod(Metric metric, int seconds);
}
