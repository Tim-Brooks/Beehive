package fault.java.metrics;

import fault.java.Status;

/**
 * Created by timbrooks on 11/10/14.
 */
public class ActionMetrics implements IActionMetrics {

    private final int[] errorMetrics;
    private final int[] successMetrics;
    private final int[] timeoutMetrics;
    private long advanceSlotTimeInMillis;
    private int slotNumber;
    private int totalSlots;

    public ActionMetrics() {
        this.totalSlots = 1000;
        this.errorMetrics = new int[totalSlots];
        this.successMetrics = new int[totalSlots];
        this.timeoutMetrics = new int[totalSlots];
        this.slotNumber = 0;
        this.advanceSlotTimeInMillis = System.currentTimeMillis() + 1000;
    }

    @Override
    public int getFailuresForTimePeriod(int milliseconds) {
        long currentTimestamp = System.currentTimeMillis();
        advanceToCurrentSlot(currentTimestamp);

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
        long currentTimestamp = System.currentTimeMillis();
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
        if (currentTimestamp < advanceSlotTimeInMillis) {
            metrics[slotNumber]++;
        } else {
            advanceToCurrentSlot(currentTimestamp);
        }
    }

    private void advanceToCurrentSlot(long currentTimestamp) {
        int slotsToAdvance =  1 + (int) ((advanceSlotTimeInMillis - currentTimestamp) / 1000);
        int newSlotNumber = slotsToAdvance + slotNumber;
        if (newSlotNumber >= totalSlots) {
            newSlotNumber = newSlotNumber - totalSlots;
        }
        this.slotNumber = newSlotNumber;
        this.advanceSlotTimeInMillis = advanceSlotTimeInMillis + (1000 * slotsToAdvance);
    }

}
