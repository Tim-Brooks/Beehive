package fault.metrics;

import fault.RejectionReason;
import fault.Status;

/**
 * Created by timbrooks on 11/20/14.
 */
public interface ActionMetrics {
    public void reportActionResult(Status status);

    public void reportRejectionAction(RejectionReason reason);

    public int getFailuresForTimePeriod(int milliseconds);

    public int getSuccessesForTimePeriod(int milliseconds);

    public int getErrorsForTimePeriod(int milliseconds);

    public int getTimeoutsForTimePeriod(int milliseconds);

    public int getSecondsTracked();
}
