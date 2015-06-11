package fault.metrics;

import fault.utils.SystemTime;

import java.util.HashMap;
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

    public DefaultActionMetrics(int slotsToTrack, int resolution, TimeUnit slotUnit) {
        this(slotsToTrack, TimeUnit.MILLISECONDS.convert(resolution, slotUnit));
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
        assertValidArgument(seconds);

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

    @Override
    public Map<Object, Object> snapshot(long timeAmount, TimeUnit timeUnit) {
        long longSeconds = TimeUnit.SECONDS.convert(timeAmount, timeUnit);
        assertValidArgument(longSeconds);
        int seconds = (int) longSeconds;


        Slot[] slotArray = collectSlots(seconds);
        long total = 0;
        long successes = 0;
        long timeouts = 0;
        long errors = 0;
        long maxConcurrency = 0;
        long queueFull = 0;
        long circuitOpen = 0;

        long maxTotal = 0;
        long maxSuccesses = 0;
        long maxTimeouts = 0;
        long maxErrors = 0;
        long maxMaxConcurrency = 0;
        long maxQueueFull = 0;
        long maxCircuitOpen = 0;
        for (Slot slot : slotArray) {
            long slotSuccesses = slot.getMetric(Metric.SUCCESS).longValue();
            long slotErrors = slot.getMetric(Metric.ERROR).longValue();
            long slotTimeouts = slot.getMetric(Metric.TIMEOUT).longValue();
            long slotMaxConcurrency = slot.getMetric(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED).longValue();
            long slotCircuitOpen = slot.getMetric(Metric.CIRCUIT_OPEN).longValue();
            long slotQueueFull = slot.getMetric(Metric.QUEUE_FULL).longValue();
            long slotTotal = slotSuccesses + slotErrors + slotTimeouts + slotMaxConcurrency + slotCircuitOpen +
                    slotQueueFull;
            total = total + slotTotal;
            maxTotal = Math.max(maxTotal, slotTotal);

            successes = successes + slotSuccesses;
            maxSuccesses = Math.max(maxSuccesses, slotSuccesses);

            timeouts = timeouts + slotTimeouts;
            maxTimeouts = Math.max(maxTimeouts, slotTimeouts);

            errors = errors + slotErrors;
            maxErrors = Math.max(maxErrors, slotErrors);

            maxConcurrency = slotMaxConcurrency + maxConcurrency;
            maxMaxConcurrency = Math.max(maxMaxConcurrency, slotMaxConcurrency);

            circuitOpen = slotCircuitOpen + circuitOpen;
            maxQueueFull = Math.max(maxQueueFull, slotCircuitOpen);

            queueFull = slotQueueFull + queueFull;
            maxCircuitOpen = Math.max(maxCircuitOpen, slotQueueFull);
        }

        Map<Object, Object> metricsMap = new HashMap<>();
        metricsMap.put("total", total);
        metricsMap.put("successes", successes);
        metricsMap.put("errors", successes);
        metricsMap.put("max-concurrency", maxConcurrency);
        metricsMap.put("queue-full", queueFull);
        metricsMap.put("circuit-open", circuitOpen);

        metricsMap.put("max-total", maxTotal);
        metricsMap.put("max-successes", maxSuccesses);
        metricsMap.put("max-errors", maxErrors);
        metricsMap.put("max-max-concurrency", maxMaxConcurrency);
        metricsMap.put("max-queue-full", maxQueueFull);
        metricsMap.put("max-circuit-open", maxCircuitOpen);
        return metricsMap;
    }

    private Slot[] collectSlots(int slots) {
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

    private void assertValidArgument(long seconds) {
        if (seconds > totalSlots) {
            String message = String.format("Seconds greater than seconds tracked: [Tracked: %s, Argument: %s]",
                    totalSlots, seconds);
            throw new IllegalArgumentException(message);
        } else if (seconds <= 0) {
            String message = String.format("Seconds must be greater than 0. [Argument: %s]", seconds);
            throw new IllegalArgumentException(message);
        }
    }

}
