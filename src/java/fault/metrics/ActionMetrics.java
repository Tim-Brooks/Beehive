package fault.metrics;

import fault.Status;

/**
 * Created by timbrooks on 11/20/14.
 */
public interface ActionMetrics {
    public void reportActionResult(Status status);

    public int getFailuresForTimePeriod(int milliseconds);

    public int getSuccessesForTimePeriod(int milliseconds);

    public int getErrorsForTimePeriod(int milliseconds);

    public int getTimeoutsForTimePeriod(int milliseconds);

    public int getSecondsTracked();
}
