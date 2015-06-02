package fault.metrics;

import fault.utils.SystemTime;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by timbrooks on 6/1/15.
 */
public class MultiWriterActionMetrics {

    private final AtomicReferenceArray<Second> metrics;
    private final SystemTime systemTime;
    private final AtomicLong advanceSlotTimeInMillis;
    private final int totalSlots;
    private AtomicInteger slotNumber = new AtomicInteger(0);

    public MultiWriterActionMetrics(int secondsToTrack) {
        this(secondsToTrack, new SystemTime());
    }

    public MultiWriterActionMetrics(int secondsToTrack, SystemTime systemTime) {
        this.totalSlots = secondsToTrack;
        this.metrics = new AtomicReferenceArray<>(secondsToTrack);
        this.systemTime = systemTime;
        this.advanceSlotTimeInMillis = new AtomicLong(systemTime.currentTimeMillis() + 1000L);
    }

    public void incrementMetric(Metric metric) {
        int currentSlotNumber = slotNumber.get();
        int slotsToAdvance = slotsToAdvance();


    }

    public int getFailuresForTimePeriod(Metric metric, long milliseconds) {
        return 0;
    }

    // Unsure if this is correct
    private int advanceToCurrentSlot(int currentSlotNumber, int slotsToAdvance) {
        if (slotsToAdvance != 0) {
            int newSlot = slotsToAdvance + currentSlotNumber;
            int adjustedSlot = newSlot % totalSlots;
            if (slotNumber.compareAndSet(currentSlotNumber, adjustedSlot)) {
                for (int i = currentSlotNumber + 1; i <= newSlot; ++i) {
                    if (i < totalSlots) {
                        this.metrics.set(i, null);
                    } else {
                        this.metrics.set(i - totalSlots, null);
                    }
                }
                this.advanceSlotTimeInMillis.set(advanceSlotTimeInMillis.get() + (1000 * slotsToAdvance));
                return adjustedSlot;
            } else {
                return -1;
            }
        }
        return currentSlotNumber;
    }

    private int slotsToAdvance() {
        long currentTimestamp = systemTime.currentTimeMillis();
        if (currentTimestamp < advanceSlotTimeInMillis.get()) {
            return 0;
        }

        long advanceSlotTimeInMillis = this.advanceSlotTimeInMillis.get();
        return 1 + (int) ((currentTimestamp - advanceSlotTimeInMillis) / 1000);
    }

}
