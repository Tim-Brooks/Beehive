package fault.metrics;

import com.codahale.metrics.Meter;

/**
 * Created by timbrooks on 6/3/15.
 */
public class CodahaleMetrics implements ActionMetrics {

    private Meter successes = new Meter();
    private Meter errors = new Meter();
    private Meter timeouts = new Meter();
    private Meter circuitOpen = new Meter();
    private Meter queueFull = new Meter();
    private Meter maxConcurrencyExceeded = new Meter();

    @Override
    public void incrementMetricCount(Metric metric) {
        switch (metric) {
            case SUCCESS:
                successes.mark();
                break;
            case ERROR:
                errors.mark();
                break;
            case TIMEOUT:
                timeouts.mark();
                break;
            case CIRCUIT_OPEN:
                circuitOpen.mark();
                break;
            case QUEUE_FULL:
                queueFull.mark();
                break;
            case MAX_CONCURRENCY_LEVEL_EXCEEDED:
                maxConcurrencyExceeded.mark();
                break;
            default:
                throw new RuntimeException("Unknown metric: " + metric);
        }
    }

    @Override
    public int getMetricCountForTimePeriod(Metric metric, int seconds) {
        double value;
        switch (metric) {
            case SUCCESS:
                value = successes.getOneMinuteRate();
                break;
            case ERROR:
                value = errors.getOneMinuteRate();
                break;
            case TIMEOUT:
                value = timeouts.getOneMinuteRate();
                break;
            case CIRCUIT_OPEN:
                value = circuitOpen.getOneMinuteRate();
                break;
            case QUEUE_FULL:
                value = queueFull.getOneMinuteRate();
                break;
            case MAX_CONCURRENCY_LEVEL_EXCEEDED:
                value = maxConcurrencyExceeded.getOneMinuteRate();
                break;
            default:
                throw new RuntimeException("Unknown metric: " + metric);
        }
        return (int) value;
    }


}
