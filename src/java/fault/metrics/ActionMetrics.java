package fault.metrics;

import fault.RejectionReason;
import fault.Status;

/**
 * Created by timbrooks on 11/20/14.
 */
public interface ActionMetrics {
    void reportActionResult(Status status);

    void reportRejectionAction(RejectionReason reason);

    int getFailuresForTimePeriod(int milliseconds);

    int getSuccessesForTimePeriod(int milliseconds);

    int getErrorsForTimePeriod(int milliseconds);

    int getTimeoutsForTimePeriod(int milliseconds);

    int getCircuitOpenedRejectionsForTimePeriod(int milliseconds);

    int getQueueFullRejectionsForTimePeriod(int milliseconds);

    int getMaxConcurrencyRejectionsForTimePeriod(int milliseconds);

    int getSecondsTracked();
}
