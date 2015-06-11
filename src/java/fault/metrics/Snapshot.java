package fault.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by timbrooks on 6/11/15.
 */
public class Snapshot {

    public static Map<Object, Object> generate(Slot[] slots) {
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
        for (Slot slot : slots) {
            if (slot != null) {
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
}
