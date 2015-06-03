package fault.metrics;

import fault.utils.SystemTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;

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
        this.metrics = new DefaultActionMetrics(1, systemTime);

        when(systemTime.currentTimeMillis()).thenReturn(1L);
        metrics.incrementMetric(Metric.SUCCESS);
        when(systemTime.currentTimeMillis()).thenReturn(2L);
        metrics.incrementMetric(Metric.SUCCESS);

        when(systemTime.currentTimeMillis()).thenReturn(999L);
        assertEquals(2, metrics.getMetricForTimePeriod(Metric.SUCCESS, 1));

        when(systemTime.currentTimeMillis()).thenReturn(1000L);
        assertEquals(0, metrics.getMetricForTimePeriod(Metric.SUCCESS, 1));
    }

    @Test
    public void testMetricsTrackingTwoSeconds() {
        when(systemTime.currentTimeMillis()).thenReturn(0L);
        this.metrics = new DefaultActionMetrics(2, systemTime);

        when(systemTime.currentTimeMillis()).thenReturn(1L);
        metrics.incrementMetric(Metric.ERROR);
        when(systemTime.currentTimeMillis()).thenReturn(2L);
        metrics.incrementMetric(Metric.ERROR);

        when(systemTime.currentTimeMillis()).thenReturn(999L);
        assertEquals(2, metrics.getMetricForTimePeriod(Metric.ERROR, 1));

        when(systemTime.currentTimeMillis()).thenReturn(999L);
        assertEquals(2, metrics.getMetricForTimePeriod(Metric.ERROR, 2));

        when(systemTime.currentTimeMillis()).thenReturn(1000L);
        assertEquals(0, metrics.getMetricForTimePeriod(Metric.ERROR, 1));

        when(systemTime.currentTimeMillis()).thenReturn(1000L);
        assertEquals(2, metrics.getMetricForTimePeriod(Metric.ERROR, 2));

        when(systemTime.currentTimeMillis()).thenReturn(2000L);
        assertEquals(0, metrics.getMetricForTimePeriod(Metric.ERROR, 1));

        when(systemTime.currentTimeMillis()).thenReturn(2000L);
        assertEquals(0, metrics.getMetricForTimePeriod(Metric.ERROR, 2));
    }

    @Test
    public void testMultipleWraps() {
        when(systemTime.currentTimeMillis()).thenReturn(0L);
        this.metrics = new DefaultActionMetrics(10, systemTime);

        when(systemTime.currentTimeMillis()).thenReturn(8000L);
        metrics.incrementMetric(Metric.ERROR);

        when(systemTime.currentTimeMillis()).thenReturn(20000L);
        metrics.incrementMetric(Metric.SUCCESS);

        when(systemTime.currentTimeMillis()).thenReturn(21000L);
        assertEquals(0, metrics.getMetricForTimePeriod(Metric.ERROR, 1));
        assertEquals(0, metrics.getMetricForTimePeriod(Metric.SUCCESS, 1));
        assertEquals(1, metrics.getMetricForTimePeriod(Metric.SUCCESS, 2));
    }

    @Test
    public void concurrentTest() throws Exception {
        when(systemTime.currentTimeMillis()).thenReturn(1500L);
        this.metrics = new DefaultActionMetrics(5, systemTime);

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
        assertEquals(5000, this.metrics.getMetricForTimePeriod(Metric.CIRCUIT_OPEN, 5));
        assertEquals(5000, this.metrics.getMetricForTimePeriod(Metric.SUCCESS, 5));
        assertEquals(5000, this.metrics.getMetricForTimePeriod(Metric.ERROR, 5));
        assertEquals(5000, this.metrics.getMetricForTimePeriod(Metric.TIMEOUT, 5));
        assertEquals(5000, this.metrics.getMetricForTimePeriod(Metric.QUEUE_FULL, 5));
        assertEquals(5000, this.metrics.getMetricForTimePeriod(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 5));

        assertEquals(1000, this.metrics.getMetricForTimePeriod(Metric.CIRCUIT_OPEN, 1));
        assertEquals(1000, this.metrics.getMetricForTimePeriod(Metric.SUCCESS, 1));
        assertEquals(1000, this.metrics.getMetricForTimePeriod(Metric.ERROR, 1));
        assertEquals(1000, this.metrics.getMetricForTimePeriod(Metric.TIMEOUT, 1));
        assertEquals(1000, this.metrics.getMetricForTimePeriod(Metric.QUEUE_FULL, 1));
        assertEquals(1000, this.metrics.getMetricForTimePeriod(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 1));

        when(systemTime.currentTimeMillis()).thenReturn(6500L);
        assertEquals(4000, this.metrics.getMetricForTimePeriod(Metric.CIRCUIT_OPEN, 5));
        assertEquals(4000, this.metrics.getMetricForTimePeriod(Metric.SUCCESS, 5));
        assertEquals(4000, this.metrics.getMetricForTimePeriod(Metric.ERROR, 5));
        assertEquals(4000, this.metrics.getMetricForTimePeriod(Metric.TIMEOUT, 5));
        assertEquals(4000, this.metrics.getMetricForTimePeriod(Metric.QUEUE_FULL, 5));
        assertEquals(4000, this.metrics.getMetricForTimePeriod(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED, 5));
    }

    private void fireThreads(final ActionMetrics metrics, int num) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(num);

        for (int i = 0; i < num; ++i) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < 100; ++j) {
                        metrics.incrementMetric(Metric.SUCCESS);
                        metrics.incrementMetric(Metric.ERROR);
                        metrics.incrementMetric(Metric.TIMEOUT);
                        metrics.incrementMetric(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED);
                        metrics.incrementMetric(Metric.QUEUE_FULL);
                        metrics.incrementMetric(Metric.CIRCUIT_OPEN);
                    }
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
    }

}
