package fault.metrics;

import fault.RejectionReason;
import fault.Status;

/**
 * Created by timbrooks on 6/1/15.
 */
public class MultiWriterActionMetrics implements ActionMetrics {
    @Override
    public void reportActionResult(Status status) {

    }

    @Override
    public void reportRejectionAction(RejectionReason reason) {

    }

    @Override
    public int getFailuresForTimePeriod(int milliseconds) {
        return 0;
    }

    @Override
    public int getSuccessesForTimePeriod(int milliseconds) {
        return 0;
    }

    @Override
    public int getErrorsForTimePeriod(int milliseconds) {
        return 0;
    }

    @Override
    public int getTimeoutsForTimePeriod(int milliseconds) {
        return 0;
    }

    @Override
    public int getCircuitOpenedRejectionsForTimePeriod(int milliseconds) {
        return 0;
    }

    @Override
    public int getQueueFullRejectionsForTimePeriod(int milliseconds) {
        return 0;
    }

    @Override
    public int getMaxConcurrencyRejectionsForTimePeriod(int milliseconds) {
        return 0;
    }
}
