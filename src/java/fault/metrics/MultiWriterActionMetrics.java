package fault.metrics;

import fault.utils.SystemTime;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Created by timbrooks on 6/1/15.
 */
public class MultiWriterActionMetrics implements NewActionMetrics {

    private final AtomicReferenceArray<Second> metrics;
    private final SystemTime systemTime;
    private final int totalSlots;
    private final long startTime;

    public MultiWriterActionMetrics(int secondsToTrack) {
        this(secondsToTrack, new SystemTime());
    }

    public MultiWriterActionMetrics(int secondsToTrack, SystemTime systemTime) {
        this.startTime = systemTime.currentTimeMillis();
        this.totalSlots = secondsToTrack;
        this.metrics = new AtomicReferenceArray<>(secondsToTrack);
        this.systemTime = systemTime;

        for (int i = 0; i < secondsToTrack; ++i) {
            metrics.set(i, new Second(i));
        }
    }

    @Override
    public void incrementMetric(Metric metric) {
        int currentSecond = currentSecond();
        int currentSlot = currentSecond % totalSlots;
        for (; ; ) {
            Second second = metrics.get(currentSlot);
            if (second.getSecond() == currentSecond) {
                second.incrementMetric(metric);
                break;
            } else {
                Second newSecond = new Second(currentSecond);
                if (metrics.compareAndSet(currentSlot, second, newSecond)) {
                    newSecond.incrementMetric(metric);
                    break;
                }
            }
        }

    }

    @Override
    public int getMetricForTimePeriod(Metric metric, int seconds) {
        System.out.println(metrics);
        if (seconds > totalSlots) {
            String message = String.format("Seconds greater than seconds tracked: [Tracked: %s, Argument: %s]",
                    totalSlots, seconds);
            throw new IllegalArgumentException(message);
        } else if (seconds <= 0) {
            String message = String.format("Seconds must be greater than 0. [Argument: %s]", seconds);
            throw new IllegalArgumentException(message);
        }

        int currentSecond = currentSecond();
        int startSecond = currentSecond - seconds;
        int adjustedStartSecond = startSecond >= 0 ? startSecond : 0;

        int totalEvents = 0;
        for (int i = adjustedStartSecond; i <= currentSecond; ++i) {
            int slot = i % totalSlots;
            Second second = metrics.get(slot);
            if (second.getSecond() == i) {
                totalEvents = totalEvents + second.getMetric(metric).intValue();
            }
        }

        return totalEvents;
    }

    private int currentSecond() {
        return (int) ((systemTime.currentTimeMillis() - startTime) / 1000);
    }

}
