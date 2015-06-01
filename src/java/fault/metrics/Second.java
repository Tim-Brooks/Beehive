package fault.metrics;

import fault.concurrent.LongAdder;

/**
 * Created by timbrooks on 6/1/15.
 */
public class Second {

    private final LongAdder successes = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder circuitOpen = new LongAdder();
    private final LongAdder queueFull = new LongAdder();
    private final LongAdder maxConcurrencyExceeded = new LongAdder();

    public void incrementMetric(Metric metric) {
        switch (metric) {
            case SUCCESS:
                successes.increment();
                break;
            case ERROR:
                errors.increment();
                break;
            case TIMEOUT:
                timeouts.increment();
                break;
            case CIRCUIT_OPEN:
                circuitOpen.increment();
                break;
            case QUEUE_FULL:
                queueFull.increment();
                break;
            case MAX_CONCURRENCY_LEVEL_EXCEEDED:
                maxConcurrencyExceeded.increment();
                break;
            default:
                throw new RuntimeException("Unknown metric: " + metric);
        }
    }
}
