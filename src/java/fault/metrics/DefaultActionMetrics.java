package fault.metrics;

import fault.utils.SystemTime;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by timbrooks on 6/1/15.
 */
public class DefaultActionMetrics implements ActionMetrics {

    private final AtomicReferenceArray<Slot> metrics;
    private final SystemTime systemTime;
    private final int totalSlots;
    private final long startTime;
    private final long millisecondsPerSlot;

    public DefaultActionMetrics(int slotsToTrack) {
        this(slotsToTrack, 1000);
    }

    public DefaultActionMetrics(int slotsToTrack, long millisecondsPerSlot) {
        this(slotsToTrack, millisecondsPerSlot, new SystemTime());

    }

    public DefaultActionMetrics(int secondsToTrack, long millisecondsPerSlot, SystemTime systemTime) {
        this.millisecondsPerSlot = millisecondsPerSlot;
        this.startTime = systemTime.currentTimeMillis();
        this.totalSlots = secondsToTrack;
        this.metrics = new AtomicReferenceArray<>(secondsToTrack);
        this.systemTime = systemTime;

        for (int i = 0; i < secondsToTrack; ++i) {
            metrics.set(i, new Slot(i));
        }
    }

    @Override
    public void incrementMetricCount(Metric metric) {
        int absoluteSlot = currentAbsoluteSlot();
        int relativeSlot = absoluteSlot % totalSlots;
        for (; ; ) {
            Slot slot = metrics.get(relativeSlot);
            if (slot.getAbsoluteSlot() == absoluteSlot) {
                slot.incrementMetric(metric);
                break;
            } else {
                Slot newSlot = new Slot(absoluteSlot);
                if (metrics.compareAndSet(relativeSlot, slot, newSlot)) {
                    newSlot.incrementMetric(metric);
                    break;
                }
            }
        }

    }

    @Override
    public long getMetricCountForTimePeriod(Metric metric, int seconds) {
        if (seconds > totalSlots) {
            String message = String.format("Seconds greater than seconds tracked: [Tracked: %s, Argument: %s]",
                    totalSlots, seconds);
            throw new IllegalArgumentException(message);
        } else if (seconds <= 0) {
            String message = String.format("Seconds must be greater than 0. [Argument: %s]", seconds);
            throw new IllegalArgumentException(message);
        }

        int absoluteSlot = currentAbsoluteSlot();
        int startSlot = 1 + absoluteSlot - seconds;
        int adjustedStartSlot = startSlot >= 0 ? startSlot : 0;

        int count = 0;
        for (int i = adjustedStartSlot; i <= absoluteSlot; ++i) {
            int relativeSlot = i % totalSlots;
            Slot slot = metrics.get(relativeSlot);
            if (slot.getAbsoluteSlot() == i) {
                count = count + slot.getMetric(metric).intValue();
            }
        }

        return count;
    }

    private int currentAbsoluteSlot() {
        return (int) ((systemTime.currentTimeMillis() - startTime) / millisecondsPerSlot);
    }

}
