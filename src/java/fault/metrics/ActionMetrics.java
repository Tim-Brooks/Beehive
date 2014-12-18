package fault.metrics;

import fault.Status;

/**
 * Created by timbrooks on 11/20/14.
 */
public interface ActionMetrics {
    int getFailuresForTimePeriod(int milliseconds);

    void reportActionResult(Status status);

    int getSuccessesForTimePeriod(int milliseconds);

    int getErrorsForTimePeriod(int milliseconds);

    int getTimeoutsForTimePeriod(int milliseconds);
}
