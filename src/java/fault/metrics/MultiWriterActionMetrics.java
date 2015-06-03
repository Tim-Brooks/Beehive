package fault.metrics;

import fault.utils.SystemTime;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by timbrooks on 6/1/15.
 */
public class MultiWriterActionMetrics implements NewActionMetrics {

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
        this.metrics.set(0, new Second());
        this.systemTime = systemTime;
        this.advanceSlotTimeInMillis = new AtomicLong(systemTime.currentTimeMillis() + 1000L);
    }

    @Override
    public void incrementMetric(Metric metric) {
        int currentSlot = currentSlot();
        for (; ; ) {
            int previousSlotNumber = slotNumber.get();
            if (currentSlot == previousSlotNumber) {
                metrics.get(currentSlot).incrementMetric(metric);
                break;
            } else {
                if (slotNumber.compareAndSet(previousSlotNumber, currentSlot)) {
                    // How do we set new without losing data?
                    metrics.set(currentSlot, new Second());

                    metrics.get(currentSlot).incrementMetric(metric);
                    // Clearly does not work right now. Current slot is normalized. Metrics set is not normalized.
                    for (int i = previousSlotNumber + 1; i <= currentSlot; ++i) {
                        if (i < totalSlots) {
                            this.metrics.set(i, null);
                        } else {
                            this.metrics.set(i - totalSlots, null);
                        }
                    }
                    break;
                }

            }
        }

    }

    @Override
    public int getMetricForTimePeriod(Metric metric, long milliseconds) {
        int seconds = (int) milliseconds / 1000;
        int currentTimeSlot = currentSlot();
        int mostRecentSlot = slotNumber.get();

        int difference = seconds - (currentTimeSlot - mostRecentSlot);

        int totalEvents = 0;
        for (int i = mostRecentSlot - difference; i <= mostRecentSlot; ++i) {
            if (i < 0) {
                totalEvents = totalEvents + metrics.get(totalSlots + i).getMetric(metric).intValue();
            } else {
                totalEvents = totalEvents + metrics.get(i).getMetric(metric).intValue();
            }
        }


        return totalEvents;
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
        return (int) ((systemTime.currentTimeMillis() - startTime) % (totalSlots * 1000)) / 1000;
    }

}
