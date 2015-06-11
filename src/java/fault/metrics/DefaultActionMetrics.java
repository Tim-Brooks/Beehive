package fault.metrics;

import fault.utils.SystemTime;

import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    public DefaultActionMetrics() {
        this(3600, 1, TimeUnit.SECONDS);
    }

    public DefaultActionMetrics(int slotsToTrack, long resolution, TimeUnit slotUnit) {
        this(slotsToTrack, resolution, slotUnit, new SystemTime());
    }

    public DefaultActionMetrics(int slotsToTrack, long resolution, TimeUnit slotUnit, SystemTime systemTime) {
        long millisecondsPerSlot = TimeUnit.MILLISECONDS.convert(resolution, slotUnit);
        if (100 > millisecondsPerSlot) {
            throw new IllegalArgumentException(String.format("Too low of resolution: [%s milliseconds]. 100 " +
                    "milliseconds is the minimum resolution.", millisecondsPerSlot));
        }

        this.millisecondsPerSlot = millisecondsPerSlot;
        this.startTime = systemTime.currentTimeMillis();
        this.totalSlots = slotsToTrack;
        this.metrics = new AtomicReferenceArray<>(slotsToTrack);
        this.systemTime = systemTime;

        for (int i = 0; i < slotsToTrack; ++i) {
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
    public long getMetricCountForTimePeriod(Metric metric, long timePeriod, TimeUnit timeUnit) {
        int slots = convertToSlots(timePeriod, timeUnit);

        int absoluteSlot = currentAbsoluteSlot();
        int startSlot = 1 + absoluteSlot - slots;
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

    @Override
    public Map<Object, Object> snapshot(long timePeriod, TimeUnit timeUnit) {
        int slots = convertToSlots(timePeriod, timeUnit);
        return Snapshot.generate(collectActiveSlots(slots));

    }

    private Slot[] collectActiveSlots(int slots) {
        int absoluteSlot = currentAbsoluteSlot();
        int startSlot = 1 + absoluteSlot - slots;
        int adjustedStartSlot = startSlot >= 0 ? startSlot : 0;

        Slot[] slotArray = new Slot[slots];
        int j = 0;
        for (int i = adjustedStartSlot; i <= absoluteSlot; ++i) {
            int relativeSlot = i % totalSlots;
            Slot slot = metrics.get(relativeSlot);
            if (slot.getAbsoluteSlot() == i) {
                slotArray[j] = slot;
            }
            ++j;
        }
        return slotArray;
    }

    private int currentAbsoluteSlot() {
        return (int) ((systemTime.currentTimeMillis() - startTime) / millisecondsPerSlot);
    }

    private int convertToSlots(long timePeriod, TimeUnit timeUnit) {
        long longSlots = TimeUnit.MILLISECONDS.convert(timePeriod, timeUnit) / millisecondsPerSlot;

        if (longSlots > totalSlots) {
            String message = String.format("Slots greater than slots tracked: [Tracked: %s, Argument: %s]",
                    totalSlots, longSlots);
            throw new IllegalArgumentException(message);
        } else if (longSlots <= 0) {
            String message = String.format("Slots must be greater than 0. [Argument: %s]", longSlots);
            throw new IllegalArgumentException(message);
        }
        return (int) longSlots;
    }

}
