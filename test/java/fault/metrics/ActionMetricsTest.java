package fault.metrics;

import fault.Status;
import fault.utils.TimeProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Random;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void testMixedResultsCorrectReporting() {
        int errorCount = 0;
        int successCount = 0;
        int timeoutCount = 0;

        Random random = new Random();

        for (int i = 0; i < 100; ++i) {
            if (random.nextBoolean()) {
                when(timeProvider.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportActionResult(Status.ERROR);
                ++errorCount;
            }
            if (random.nextBoolean()) {
                when(timeProvider.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportActionResult(Status.SUCCESS);
                ++successCount;
            }
            if (random.nextBoolean()) {
                when(timeProvider.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportActionResult(Status.TIMED_OUT);
                ++timeoutCount;

            }
        }
        when(timeProvider.currentTimeMillis()).thenReturn(1999L, 1999L, 1999L);
        assertEquals(successCount, actionMetrics.getSuccessesForTimePeriod(1000));
        assertEquals(errorCount, actionMetrics.getErrorsForTimePeriod(1000));
        assertEquals(timeoutCount, actionMetrics.getTimeoutsForTimePeriod(1000));

    }

    @Test
    public void testMetricsOnlyReportForTimePeriod() {
        for (int i = 1; i < 4; ++i) {
            long timestamp = 500L * i;
            when(timeProvider.currentTimeMillis()).thenReturn(timestamp, timestamp, timestamp);
            actionMetrics.reportActionResult(Status.ERROR);
            actionMetrics.reportActionResult(Status.SUCCESS);
            actionMetrics.reportActionResult(Status.TIMED_OUT);
        }

        when(timeProvider.currentTimeMillis()).thenReturn(2000L, 2000L, 2000L, 2000L, 2000L, 2000L);
        assertEquals(3, actionMetrics.getSuccessesForTimePeriod(2000));
        assertEquals(2, actionMetrics.getSuccessesForTimePeriod(1000));
        assertEquals(3, actionMetrics.getErrorsForTimePeriod(2000));
        assertEquals(2, actionMetrics.getErrorsForTimePeriod(1000));
        assertEquals(3, actionMetrics.getTimeoutsForTimePeriod(2000));
        assertEquals(2, actionMetrics.getTimeoutsForTimePeriod(1000));
    }

    @Test
    public void testWrappingOfBuffer() {
        for (int i = 1; i < 1000; ++i) {
            long timestamp = 1000L * i;
            when(timeProvider.currentTimeMillis()).thenReturn(timestamp, timestamp, timestamp);
            actionMetrics.reportActionResult(Status.ERROR);
            actionMetrics.reportActionResult(Status.SUCCESS);
            actionMetrics.reportActionResult(Status.TIMED_OUT);
        }

        when(timeProvider.currentTimeMillis()).thenReturn(1005000L);
        actionMetrics.reportActionResult(Status.ERROR);
        actionMetrics.reportActionResult(Status.SUCCESS);
        actionMetrics.reportActionResult(Status.TIMED_OUT);

        assertEquals(2, actionMetrics.getSuccessesForTimePeriod(6000));

    }
}
