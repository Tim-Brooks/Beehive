package fault.metrics;

import fault.Status;
import fault.utils.TimeProvider;

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
        int slotsBack = milliseconds / 1000;
        if (slotsBack > totalSlots) {
            throw new RuntimeException("That amount of time is not tracked.");
        }

        int slotsToAdvance = slotsToAdvance();
        if (slotsToAdvance > slotsBack) {
            return 0;
        }

        int totalErrors = 0;
        int currentSlot = slotNumber.get();
        for (int i = currentSlot - (slotsBack - slotsToAdvance); i <= currentSlot; ++i) {
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
        int slotsToAdvance = slotsToAdvance();
        int slotNumber = advanceToCurrentSlot(currentSlotNumber, slotsToAdvance);

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
        int slotsBack = milliseconds / 1000;
        if (slotsBack > totalSlots) {
            throw new RuntimeException("That amount of time is not tracked.");
        }

        int slotsToAdvance = slotsToAdvance();
        if (slotsToAdvance > slotsBack) {
            return 0;
        }

        int totalEvents = 0;
        int currentSlot = slotNumber.get();
        for (int i = currentSlot - (slotsBack - slotsToAdvance); i <= currentSlot; ++i) {
            if (i < 0) {
                totalEvents = totalEvents + metricsArray.get(totalSlots + i);
            } else {
                totalEvents = totalEvents + metricsArray.get(i);
            }
        }

        return totalEvents;
    }

    private int advanceToCurrentSlot(int currentSlotNumber, int slotsToAdvance) {
        if (slotsToAdvance != 0) {
            int newSlot = slotsToAdvance + currentSlotNumber;
            for (int i = currentSlotNumber + 1; i <= newSlot; ++i) {
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
            int adjustedNewSlot = newSlot < totalSlots ? newSlot : newSlot - totalSlots;
            this.slotNumber.lazySet(adjustedNewSlot);
            this.advanceSlotTimeInMillis.lazySet(advanceSlotTimeInMillis.get() + (1000 * slotsToAdvance));
            return adjustedNewSlot;
        }
        return currentSlotNumber;
    }

    private int slotsToAdvance() {
        long currentTimestamp = timeProvider.currentTimeMillis();
        if (currentTimestamp < advanceSlotTimeInMillis.get()) {
            return 0;
        }

        long advanceSlotTimeInMillis = this.advanceSlotTimeInMillis.get();
        int slotsToAdvance = 1 + (int) ((currentTimestamp - advanceSlotTimeInMillis) / 1000);
//        int newSlotNumber = slotsToAdvance + currentSlotNumber;
//        if (newSlotNumber >= totalSlots) {
//            newSlotNumber = newSlotNumber - totalSlots;
//        }
        return slotsToAdvance;
    }
}
