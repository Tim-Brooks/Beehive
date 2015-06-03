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
    private final long startTime;
    private AtomicInteger slotNumber = new AtomicInteger(0);

    public MultiWriterActionMetrics(int secondsToTrack) {
        this(secondsToTrack, new SystemTime());
    }

    public MultiWriterActionMetrics(int secondsToTrack, SystemTime systemTime) {
        this.startTime = systemTime.currentTimeMillis();
        this.totalSlots = secondsToTrack;
        this.metrics = new AtomicReferenceArray<>(secondsToTrack);
        this.systemTime = systemTime;
        this.advanceSlotTimeInMillis = new AtomicLong(systemTime.currentTimeMillis() + 1000L);
    }

    public void incrementMetric(Metric metric) {
        int currentSlot = currentSlot();
        for (; ; ) {
            int previousSlotNumber = slotNumber.get();
            if (currentSlot == previousSlotNumber) {
                metrics.get(currentSlot).incrementMetric(metric);
                break;
            } else {
                if (slotNumber.compareAndSet(previousSlotNumber, currentSlot)) {
                    metrics.get(currentSlot).incrementMetric(metric);
                    // Null out stuff
                    break;
                }

            }
        }

    }

    public int getFailuresForTimePeriod(Metric metric, long milliseconds) {


        return 0;
    }

    // Unsure if this is correct
    private int advanceToCurrentSlot(int currentSlotNumber, int slotsToAdvance) {
        if (slotsToAdvance == 0) {
            return currentSlotNumber;
        } else {
            int newSlot = slotsToAdvance + currentSlotNumber;
            int adjustedSlot = newSlot % totalSlots;
            if (slotNumber.compareAndSet(currentSlotNumber, adjustedSlot)) {
                // This presents a lot of races with changing the slotNumber. Mabye time should be the slotnumber?
                this.advanceSlotTimeInMillis.set(advanceSlotTimeInMillis.get() + (1000 * slotsToAdvance));
                for (int i = currentSlotNumber + 1; i <= newSlot; ++i) {
                    if (i < totalSlots) {
                        this.metrics.set(i, null);
                    } else {
                        this.metrics.set(i - totalSlots, null);
                    }
                }
                return adjustedSlot;
            } else {
                return -1;
            }
        }
    }

    private int currentSlot() {
        return (int) ((systemTime.currentTimeMillis() - startTime) % totalSlots) / 1000;
    }

}
