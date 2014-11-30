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
    private final int totalSlots;
    private final AtomicLong advanceSlotTimeInMillis;
    private final AtomicInteger slotNumber;

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
        advanceToCurrentSlot();

        int slotsBack = milliseconds / 1000;
        int totalErrors = 0;
        int slotNumber = this.slotNumber.get();
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
        advanceToCurrentSlot();
        int slotNumber = this.slotNumber.get();

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

        metrics.lazySet(slotNumber, metrics.get(slotNumber) + 1);
    }

    private int getEventCountForTimePeriod(int milliseconds, AtomicIntegerArray metricsArray) {
        advanceToCurrentSlot();

        int slotsBack = milliseconds / 1000;
        int totalEvents = 0;
        int slotNumber = this.slotNumber.get();

        for (int i = slotNumber - slotsBack; i <= slotNumber; ++i) {
            if (i < 0) {
                totalEvents = totalEvents + metricsArray.get(totalSlots + i);
            } else {
                totalEvents = totalEvents + metricsArray.get(i);
            }
        }
        return totalEvents;
    }

    private void advanceToCurrentSlot() {
        long currentTimestamp = timeProvider.currentTimeMillis();
        if (currentTimestamp < advanceSlotTimeInMillis.get()) {
            return;
        }

        long advanceSlotTimeInMillis = this.advanceSlotTimeInMillis.get();
        long l = (currentTimestamp - advanceSlotTimeInMillis) / 1000;
        int slotsToAdvance = 1 + (int) l;
        int newSlotNumber = slotsToAdvance + slotNumber.get();
        if (newSlotNumber >= totalSlots) {
            newSlotNumber = newSlotNumber - totalSlots;
        }
        this.slotNumber.lazySet(newSlotNumber);
        this.advanceSlotTimeInMillis.lazySet(advanceSlotTimeInMillis + (1000 * slotsToAdvance));
    }

}
