package fault.java.metrics;

import fault.java.Status;
import fault.java.utils.TimeProvider;

/**
 * Created by timbrooks on 11/10/14.
 */
public class ActionMetrics implements IActionMetrics {

    private final int[] errorMetrics;
    private final int[] successMetrics;
    private final int[] timeoutMetrics;
    private final TimeProvider timeProvider;
    private long advanceSlotTimeInMillis;
    private int slotNumber;
    private int totalSlots;

    public ActionMetrics() {
        this(new TimeProvider());
    }

    public ActionMetrics(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        this.totalSlots = 1000;
        this.errorMetrics = new int[totalSlots];
        this.successMetrics = new int[totalSlots];
        this.timeoutMetrics = new int[totalSlots];
        this.slotNumber = 0;
        this.advanceSlotTimeInMillis = timeProvider.currentTimeMillis() + 1000L;
    }

    @Override
    public int getFailuresForTimePeriod(int milliseconds) {
        long currentTimestamp = timeProvider.currentTimeMillis();
        if (currentTimestamp >= advanceSlotTimeInMillis) {
            advanceToCurrentSlot(currentTimestamp);
        }

        int slotsBack = milliseconds / 1000;
        int totalErrors = 0;
        for (int i = slotNumber - slotsBack; i <= slotNumber; ++i) {
            if (i < 0) {
                totalErrors = totalErrors + errorMetrics[totalSlots + i] + timeoutMetrics[totalSlots + i];
            } else {
                totalErrors = totalErrors + errorMetrics[i] + timeoutMetrics[i];
            }
        }

        return totalErrors;
    }

    @Override
    public void reportActionResult(Status status) {
        long currentTimestamp = timeProvider.currentTimeMillis();
        int[] metrics = this.errorMetrics;
        switch (status) {
            case SUCCESS:
                metrics = this.successMetrics;
                break;
            case ERROR:
                metrics = this.errorMetrics;
                break;
            case TIMED_OUT:
                metrics = this.timeoutMetrics;
                break;
        }
        if (currentTimestamp >= advanceSlotTimeInMillis) {
            advanceToCurrentSlot(currentTimestamp);
        }

        ++metrics[slotNumber];
    }

    private void advanceToCurrentSlot(long currentTimestamp) {
        long l = (currentTimestamp - advanceSlotTimeInMillis) / 1000;
        int slotsToAdvance =  1 + (int) l;
        int newSlotNumber = slotsToAdvance + slotNumber;
        if (newSlotNumber >= totalSlots) {
            newSlotNumber = newSlotNumber - totalSlots;
        }
        this.slotNumber = newSlotNumber;
        this.advanceSlotTimeInMillis = advanceSlotTimeInMillis + (1000 * slotsToAdvance);
    }

}
