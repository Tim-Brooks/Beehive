package fault.java.metrics;

import fault.java.Status;
import fault.java.utils.TimeProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Created by timbrooks on 11/23/14.
 */
public class ActionMetricsTest {

    @Mock
    private TimeProvider timeProvider;

    private ActionMetrics actionMetrics;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(timeProvider.currentTimeMillis()).thenReturn(0L);
        this.actionMetrics = new ActionMetrics(1000, timeProvider);
    }

    @Test
    public void testMetricsReportsNoErrorsIfNoErrorsOrTimeouts() {
        when(timeProvider.currentTimeMillis()).thenReturn(1000L, 1000L);
        actionMetrics.reportActionResult(Status.SUCCESS);

        assertEquals(0, actionMetrics.getFailuresForTimePeriod(1000));
    }

    @Test
    public void testMetricsReportCorrectFailureCount() {
        int errorCount = new Random().nextInt(100);
        for (int i = 0; i < errorCount; ++i) {
            when(timeProvider.currentTimeMillis()).thenReturn(500L);
            actionMetrics.reportActionResult(Status.ERROR);
        }
        when(timeProvider.currentTimeMillis()).thenReturn(1999L);
        assertEquals(errorCount, actionMetrics.getFailuresForTimePeriod(1000));
    }

    @Test
    public void testMetricsReportsNoSuccessesIfNoSuccesses() {
        when(timeProvider.currentTimeMillis()).thenReturn(1000L, 1000L, 1000L);

        actionMetrics.reportActionResult(Status.ERROR);
        actionMetrics.reportActionResult(Status.TIMED_OUT);

        assertEquals(0, actionMetrics.getSuccessesForTimePeriod(1000));
    }

    @Test
    public void testMetricsReportCorrectSuccessCount() {
        int successCount = new Random().nextInt(100);
        for (int i = 0; i < successCount; ++i) {
            when(timeProvider.currentTimeMillis()).thenReturn(500L);
            actionMetrics.reportActionResult(Status.SUCCESS);
        }
        when(timeProvider.currentTimeMillis()).thenReturn(1999L);
        assertEquals(successCount, actionMetrics.getSuccessesForTimePeriod(1000));
    }
}
