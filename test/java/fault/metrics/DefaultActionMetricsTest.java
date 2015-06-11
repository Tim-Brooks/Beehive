package fault.metrics;

import fault.utils.SystemTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Created by timbrooks on 6/3/15.
 */
public class DefaultActionMetricsTest {

    @Mock
    private SystemTime systemTime;

    private DefaultActionMetrics metrics;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMetricsEdgeScenario() {
        when(systemTime.currentTimeMillis()).thenReturn(0L);
        metrics = new DefaultActionMetrics(1, 1, TimeUnit.SECONDS, systemTime);

        when(systemTime.currentTimeMillis()).thenReturn(1L);
        metrics.incrementMetricCount(Metric.SUCCESS);
        when(systemTime.currentTimeMillis()).thenReturn(2L);
        metrics.incrementMetricCount(Metric.SUCCESS);

        when(systemTime.currentTimeMillis()).thenReturn(999L);
        assertEquals(2, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, 1, TimeUnit.SECONDS));

        when(systemTime.currentTimeMillis()).thenReturn(1000L);
        assertEquals(0, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, 1, TimeUnit.SECONDS));
    }

    @Test
    public void testMetricsTrackingTwoSeconds() {
        TimeUnit unit = TimeUnit.SECONDS;
        long startTime = 0L;
        int slotsToTrack = 2;

        when(systemTime.currentTimeMillis()).thenReturn(startTime);
        metrics = new DefaultActionMetrics(slotsToTrack, 1, unit, systemTime);

        when(systemTime.currentTimeMillis()).thenReturn(1L);
        metrics.incrementMetricCount(Metric.ERROR);
        when(systemTime.currentTimeMillis()).thenReturn(2L);
        metrics.incrementMetricCount(Metric.ERROR);

        when(systemTime.currentTimeMillis()).thenReturn(999L);
        assertEquals(2, metrics.getMetricCountForTimePeriod(Metric.ERROR, 1, unit));

        when(systemTime.currentTimeMillis()).thenReturn(999L);
        assertEquals(2, metrics.getMetricCountForTimePeriod(Metric.ERROR, 2, unit));

        when(systemTime.currentTimeMillis()).thenReturn(1000L);
        assertEquals(0, metrics.getMetricCountForTimePeriod(Metric.ERROR, 1, unit));

        when(systemTime.currentTimeMillis()).thenReturn(1000L);
        assertEquals(2, metrics.getMetricCountForTimePeriod(Metric.ERROR, 2, unit));

        when(systemTime.currentTimeMillis()).thenReturn(2000L);
        assertEquals(0, metrics.getMetricCountForTimePeriod(Metric.ERROR, 1, unit));

        when(systemTime.currentTimeMillis()).thenReturn(2000L);
        assertEquals(0, metrics.getMetricCountForTimePeriod(Metric.ERROR, 2, unit));
    }

    @Test
    public void testMultipleWraps() {
        TimeUnit unit = TimeUnit.SECONDS;
        long startTime = 0L;
        long resolution = 1;
        long offsetTime = 50L;
        int slotsTracked = 10;
        long millisResolution = resolution * 1000;

        when(systemTime.currentTimeMillis()).thenReturn(startTime);
        metrics = new DefaultActionMetrics(slotsTracked, resolution, unit, systemTime);

        when(systemTime.currentTimeMillis()).thenReturn(offsetTime + millisResolution * 8);
        metrics.incrementMetricCount(Metric.ERROR);

        when(systemTime.currentTimeMillis()).thenReturn(offsetTime + millisResolution * 20);
        metrics.incrementMetricCount(Metric.SUCCESS);

        when(systemTime.currentTimeMillis()).thenReturn(offsetTime + millisResolution * 21);
        assertEquals(0, metrics.getMetricCountForTimePeriod(Metric.ERROR, resolution, unit));
        assertEquals(0, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, resolution, unit));
        assertEquals(1, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, resolution * 2, unit));
    }

    @Test
    public void testMillisecondResolution() {
        TimeUnit unit = TimeUnit.MILLISECONDS;
        long startTime = 500L;
        long resolution = 100;
        long offsetTime = 550L;
        int slotsTracked = 1000;

        when(systemTime.currentTimeMillis()).thenReturn(startTime);
        metrics = new DefaultActionMetrics(slotsTracked, resolution, unit, systemTime);

        when(systemTime.currentTimeMillis()).thenReturn(offsetTime);
        metrics.incrementMetricCount(Metric.SUCCESS);

        when(systemTime.currentTimeMillis()).thenReturn(offsetTime + resolution);
        assertEquals(0, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, resolution, unit));
        assertEquals(1, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, resolution * 2, unit));

        when(systemTime.currentTimeMillis()).thenReturn(offsetTime + resolution * (slotsTracked - 1));
        assertEquals(1, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, slotsTracked * resolution, unit));

        when(systemTime.currentTimeMillis()).thenReturn(offsetTime + resolution * slotsTracked);
        assertEquals(0, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, slotsTracked * resolution, unit));
    }

    @Test
    public void testSnapshot() {

    }

    @Test
    public void concurrentTest() throws Exception {
        when(systemTime.currentTimeMillis()).thenReturn(1500L);
        metrics = new DefaultActionMetrics(5, 1, TimeUnit.SECONDS, systemTime);

        when(systemTime.currentTimeMillis()).thenReturn(1980L);
        fireThreads(metrics, 10);

        when(systemTime.currentTimeMillis()).thenReturn(2620L);
        fireThreads(metrics, 10);

        when(systemTime.currentTimeMillis()).thenReturn(3500L);
        fireThreads(metrics, 10);

        when(systemTime.currentTimeMillis()).thenReturn(4820L);
        fireThreads(metrics, 10);

        when(systemTime.currentTimeMillis()).thenReturn(5600L);
        fireThreads(metrics, 10);

        when(systemTime.currentTimeMillis()).thenReturn(6000L);
        assertEquals(5000, metrics.getMetricCountForTimePeriod(Metric.CIRCUIT_OPEN, 5, TimeUnit.SECONDS));
        assertEquals(5000, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, 5, TimeUnit.SECONDS));
        assertEquals(5000, metrics.getMetricCountForTimePeriod(Metric.ERROR, 5, TimeUnit.SECONDS));
        assertEquals(5000, metrics.getMetricCountForTimePeriod(Metric.TIMEOUT, 5, TimeUnit.SECONDS));
        assertEquals(5000, metrics.getMetricCountForTimePeriod(Metric.QUEUE_FULL, 5, TimeUnit.SECONDS));
        assertEquals(5000, metrics.getMetricCountForTimePeriod(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 5, TimeUnit.SECONDS));

        assertEquals(1000, metrics.getMetricCountForTimePeriod(Metric.CIRCUIT_OPEN, 1, TimeUnit.SECONDS));
        assertEquals(1000, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, 1, TimeUnit.SECONDS));
        assertEquals(1000, metrics.getMetricCountForTimePeriod(Metric.ERROR, 1, TimeUnit.SECONDS));
        assertEquals(1000, metrics.getMetricCountForTimePeriod(Metric.TIMEOUT, 1, TimeUnit.SECONDS));
        assertEquals(1000, metrics.getMetricCountForTimePeriod(Metric.QUEUE_FULL, 1, TimeUnit.SECONDS));
        assertEquals(1000, metrics.getMetricCountForTimePeriod(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 1, TimeUnit.SECONDS));

        when(systemTime.currentTimeMillis()).thenReturn(6500L);
        assertEquals(4000, metrics.getMetricCountForTimePeriod(Metric.CIRCUIT_OPEN, 5, TimeUnit.SECONDS));
        assertEquals(4000, metrics.getMetricCountForTimePeriod(Metric.SUCCESS, 5, TimeUnit.SECONDS));
        assertEquals(4000, metrics.getMetricCountForTimePeriod(Metric.ERROR, 5, TimeUnit.SECONDS));
        assertEquals(4000, metrics.getMetricCountForTimePeriod(Metric.TIMEOUT, 5, TimeUnit.SECONDS));
        assertEquals(4000, metrics.getMetricCountForTimePeriod(Metric.QUEUE_FULL, 5, TimeUnit.SECONDS));
        assertEquals(4000, metrics.getMetricCountForTimePeriod(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 5, TimeUnit.SECONDS));
    }

    private void fireThreads(final ActionMetrics metrics, int num) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(num);

        for (int i = 0; i < num; ++i) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < 100; ++j) {
                        metrics.incrementMetricCount(Metric.SUCCESS);
                        metrics.incrementMetricCount(Metric.ERROR);
                        metrics.incrementMetricCount(Metric.TIMEOUT);
                        metrics.incrementMetricCount(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED);
                        metrics.incrementMetricCount(Metric.QUEUE_FULL);
                        metrics.incrementMetricCount(Metric.CIRCUIT_OPEN);
                    }
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
    }

}
