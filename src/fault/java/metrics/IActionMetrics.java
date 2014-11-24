package fault.java.metrics;

import fault.java.Status;

/**
 * Created by timbrooks on 11/20/14.
 */
public interface IActionMetrics {
    int getFailuresForTimePeriod(int milliseconds);

    void renameActionResult(Status status);
}
