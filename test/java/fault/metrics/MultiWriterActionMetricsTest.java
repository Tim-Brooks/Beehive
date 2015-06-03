package fault.metrics;

import fault.utils.SystemTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

        when(systemTime.currentTimeMillis()).thenReturn(0L);
        this.metrics = new MultiWriterActionMetrics(1, systemTime);
    }

    @Test
    public void testMetrics() {
        when(systemTime.currentTimeMillis()).thenReturn(0L);
        metrics.incrementMetric(Metric.ERROR);

        when(systemTime.currentTimeMillis()).thenReturn(1000L);
        metrics.incrementMetric(Metric.SUCCESS);

        when(systemTime.currentTimeMillis()).thenReturn(10L);
        metrics.getMetricForTimePeriod(Metric.ERROR, 1000);
    }

}
