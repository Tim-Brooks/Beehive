package fault.metrics;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by timbrooks on 6/3/15.
 */
public interface ActionMetrics {
    void incrementMetricCount(Metric metric);

    long getMetricCountForTimePeriod(Metric metric, long timePeriod, TimeUnit timeUnit);

    Map<Object, Object> snapshot(long timeAmount, TimeUnit timeUnit);
}
