package fault.java;

import fault.java.circuit.ResilientResult;

/**
 * Created by timbrooks on 11/10/14.
 */
public class ActionMetrics {

    private final int[] errorMetrics;
    private long advanceSlotTimeInMillis;
    private int slotNumber;

    public ActionMetrics() {
        this.errorMetrics = new int[1000];
        this.slotNumber = 0;
        this.advanceSlotTimeInMillis = System.currentTimeMillis() + 1000;
    }

    public int getFailuresForTimePeriod(int milliseconds) {
        long currentTimestamp = System.currentTimeMillis();
        int slotsToAdvance = slotNumber + (int) ((advanceSlotTimeInMillis - currentTimestamp) / 1000);
        this.slotNumber = slotsToAdvance;
        this.advanceSlotTimeInMillis = advanceSlotTimeInMillis + (1000 * slotsToAdvance) + 1000;

        int slotsBack = milliseconds / 1000;
        int totalErrors = 0;
        for (int i = slotNumber - slotsBack; i <= slotNumber; ++i) {
            totalErrors = totalErrors + errorMetrics[i];
        }

        return totalErrors;
    }

    public <T> void logActionResult(ResilientResult<T> resilientResult) {
        if (!resilientResult.isSuccessful()) {
            long currentTimestamp = System.currentTimeMillis();
            if (currentTimestamp < advanceSlotTimeInMillis) {
                errorMetrics[slotNumber]++;
            } else {
                int slotsToAdvance = slotNumber + (int) ((advanceSlotTimeInMillis - currentTimestamp) / 1000);
                this.slotNumber = slotsToAdvance;
                this.advanceSlotTimeInMillis = advanceSlotTimeInMillis + (1000 * slotsToAdvance) + 1000;
            }
        }

    }
}
