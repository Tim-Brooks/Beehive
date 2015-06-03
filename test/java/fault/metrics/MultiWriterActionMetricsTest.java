package fault.metrics;

import fault.utils.SystemTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Created by timbrooks on 6/3/15.
 */
public class MultiWriterActionMetricsTest {

    @Mock
    private SystemTime systemTime;

    private MultiWriterActionMetrics metrics;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMetricsEdgeScenario() {
        when(systemTime.currentTimeMillis()).thenReturn(0L);
        this.metrics = new MultiWriterActionMetrics(1, systemTime);

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
        this.metrics = new MultiWriterActionMetrics(2, systemTime);

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

}
