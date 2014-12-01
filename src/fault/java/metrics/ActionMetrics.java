package fault.java.metrics;

import fault.java.Status;
import fault.java.utils.TimeProvider;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timbrooks on 11/10/14.
 */
public class ActionMetrics implements IActionMetrics {

    private final AtomicIntegerArray errorMetrics;
    private final AtomicIntegerArray successMetrics;
    private final AtomicIntegerArray timeoutMetrics;
    private final TimeProvider timeProvider;
    private final AtomicLong advanceSlotTimeInMillis;
    private final AtomicInteger slotNumber;
    private final int totalSlots;
    private final int writerTailSlot = 0;

    public ActionMetrics(int secondsToTrack) {
        this(secondsToTrack, new TimeProvider());
    }

    public ActionMetrics(int secondsToTrack, TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        this.totalSlots = secondsToTrack;
        this.errorMetrics = new AtomicIntegerArray(totalSlots);
        this.successMetrics = new AtomicIntegerArray(totalSlots);
        this.timeoutMetrics = new AtomicIntegerArray(totalSlots);
        this.slotNumber = new AtomicInteger(0);
        this.advanceSlotTimeInMillis = new AtomicLong(timeProvider.currentTimeMillis() + 1000L);
    }

    @Override
    public int getFailuresForTimePeriod(int milliseconds) {
        int slotNumber = getUpdatedSlotNumber(this.slotNumber.get());

        int slotsBack = milliseconds / 1000;
        int totalErrors = 0;
        for (int i = slotNumber - slotsBack; i <= slotNumber; ++i) {
            if (i < 0) {
                totalErrors = totalErrors + errorMetrics.get(totalSlots + i) + timeoutMetrics.get(totalSlots + i);
            } else {
                totalErrors = totalErrors + errorMetrics.get(i) + timeoutMetrics.get(i);
            }
        }

        return totalErrors;
    }

    @Override
    public int getSuccessesForTimePeriod(int milliseconds) {
        return getEventCountForTimePeriod(milliseconds, successMetrics);
    }

    @Override
    public int getErrorsForTimePeriod(int milliseconds) {
        return getEventCountForTimePeriod(milliseconds, errorMetrics);
    }

    @Override
    public int getTimeoutsForTimePeriod(int milliseconds) {
        return getEventCountForTimePeriod(milliseconds, timeoutMetrics);
    }

    @Override
    public void reportActionResult(Status status) {
        int currentSlotNumber = slotNumber.get();
        int updatedSlotNumber = getUpdatedSlotNumber(currentSlotNumber);
        advanceToCurrentSlot(currentSlotNumber, updatedSlotNumber);

        AtomicIntegerArray metrics;
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
            default:
                return;
        }

        metrics.lazySet(updatedSlotNumber, metrics.get(updatedSlotNumber) + 1);
    }

    private int getEventCountForTimePeriod(int milliseconds, AtomicIntegerArray metricsArray) {
        int slotNumber = getUpdatedSlotNumber(this.slotNumber.get());
        int slotsBack = milliseconds / 1000;
        int totalEvents = 0;

        for (int i = slotNumber - slotsBack; i <= slotNumber; ++i) {
            if (i < 0) {
                totalEvents = totalEvents + metricsArray.get(totalSlots + i);
            } else {
                totalEvents = totalEvents + metricsArray.get(i);
            }
        }
        return totalEvents;
    }

    private void advanceToCurrentSlot(int currentSlotNumber, int updatedSlotNumber) {
        if (updatedSlotNumber != currentSlotNumber) {
            for (int i = currentSlotNumber + 1; i <= updatedSlotNumber; ++i) {
                if (i < totalSlots) {
                    errorMetrics.lazySet(i, 0);
                    successMetrics.lazySet(i, 0);
                    timeoutMetrics.lazySet(i, 0);
                } else {
                    int adjustedSlot = i - totalSlots;
                    errorMetrics.lazySet(adjustedSlot, 0);
                    successMetrics.lazySet(adjustedSlot, 0);
                    timeoutMetrics.lazySet(adjustedSlot, 0);
                }
            }
            this.slotNumber.lazySet(updatedSlotNumber);
            this.advanceSlotTimeInMillis.lazySet(advanceSlotTimeInMillis.get() + (1000 * updatedSlotNumber));
        }
    }

    private int getUpdatedSlotNumber(int currentSlotNumber) {
        long currentTimestamp = timeProvider.currentTimeMillis();
        if (currentTimestamp < advanceSlotTimeInMillis.get()) {
            return currentSlotNumber;
        }

        long advanceSlotTimeInMillis = this.advanceSlotTimeInMillis.get();
        long l = (currentTimestamp - advanceSlotTimeInMillis) / 1000;
        int slotsToAdvance = 1 + (int) l;
        int newSlotNumber = slotsToAdvance + currentSlotNumber;
        if (newSlotNumber >= totalSlots) {
            newSlotNumber = newSlotNumber - totalSlots;
        }
        return newSlotNumber;
    }

}
