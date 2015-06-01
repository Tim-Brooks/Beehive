package fault.metrics;

import fault.RejectionReason;
import fault.Status;
import fault.utils.SystemTime;
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
public class SingleWriterActionMetricsTest {

    @Mock
    private SystemTime systemTime;

    private SingleWriterActionMetrics actionMetrics;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(systemTime.currentTimeMillis()).thenReturn(0L);
        this.actionMetrics = new SingleWriterActionMetrics(1000, systemTime);
    }

    @Test
    public void testMetricsReportsNoErrorsIfNoErrorsOrTimeouts() {
        when(systemTime.currentTimeMillis()).thenReturn(1000L, 1000L);
        actionMetrics.reportActionResult(Status.SUCCESS);

        assertEquals(0, actionMetrics.getFailuresForTimePeriod(1000));
    }

    @Test
    public void testMetricsReportCorrectFailureCount() {
        int errorCount = new Random().nextInt(100);
        for (int i = 0; i < errorCount; ++i) {
            when(systemTime.currentTimeMillis()).thenReturn(500L);
            actionMetrics.reportActionResult(Status.ERROR);
        }
        when(systemTime.currentTimeMillis()).thenReturn(1999L);
        assertEquals(errorCount, actionMetrics.getFailuresForTimePeriod(1000));
    }

    @Test
    public void testMetricsReportsNoSuccessesIfNoSuccesses() {
        when(systemTime.currentTimeMillis()).thenReturn(1000L, 1000L, 1000L);

        actionMetrics.reportActionResult(Status.ERROR);
        actionMetrics.reportActionResult(Status.TIMEOUT);

        assertEquals(0, actionMetrics.getSuccessesForTimePeriod(1000));
    }

    @Test
    public void testMetricsReportCorrectSuccessCount() {
        int successCount = new Random().nextInt(100);
        for (int i = 0; i < successCount; ++i) {
            when(systemTime.currentTimeMillis()).thenReturn(500L);
            actionMetrics.reportActionResult(Status.SUCCESS);
        }
        when(systemTime.currentTimeMillis()).thenReturn(1999L);
        assertEquals(successCount, actionMetrics.getSuccessesForTimePeriod(1000));
    }

    @Test
    public void testMixedResultsCorrectReporting() {
        int errorCount = 0;
        int successCount = 0;
        int timeoutCount = 0;
        int circuitOpenCount = 0;
        int maxConcurrencyCount = 0;
        int queueFullCount = 0;

        Random random = new Random();

        for (int i = 0; i < 100; ++i) {
            if (random.nextBoolean()) {
                when(systemTime.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportActionResult(Status.ERROR);
                ++errorCount;
            }
            if (random.nextBoolean()) {
                when(systemTime.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportActionResult(Status.SUCCESS);
                ++successCount;
            }
            if (random.nextBoolean()) {
                when(systemTime.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportActionResult(Status.TIMEOUT);
                ++timeoutCount;
            }
            if (random.nextBoolean()) {
                when(systemTime.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportRejectionAction(RejectionReason.CIRCUIT_OPEN);
                ++circuitOpenCount;
            }
            if (random.nextBoolean()) {
                when(systemTime.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportRejectionAction(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
                ++maxConcurrencyCount;
            }
            if (random.nextBoolean()) {
                when(systemTime.currentTimeMillis()).thenReturn(500L);
                actionMetrics.reportRejectionAction(RejectionReason.QUEUE_FULL);
                ++queueFullCount;
            }
        }
        when(systemTime.currentTimeMillis()).thenReturn(1999L, 1999L, 1999L);
        assertEquals(successCount, actionMetrics.getSuccessesForTimePeriod(1000));
        assertEquals(errorCount, actionMetrics.getErrorsForTimePeriod(1000));
        assertEquals(timeoutCount, actionMetrics.getTimeoutsForTimePeriod(1000));
        assertEquals(queueFullCount, actionMetrics.getQueueFullRejectionsForTimePeriod(1000));
        assertEquals(maxConcurrencyCount, actionMetrics.getMaxConcurrencyRejectionsForTimePeriod(1000));
        assertEquals(circuitOpenCount, actionMetrics.getCircuitOpenedRejectionsForTimePeriod(1000));

    }

    @Test
    public void testMetricsOnlyReportForTimePeriod() {
        for (int i = 1; i < 4; ++i) {
            long timestamp = 500L * i;
            when(systemTime.currentTimeMillis()).thenReturn(timestamp, timestamp, timestamp);
            actionMetrics.reportActionResult(Status.ERROR);
            actionMetrics.reportActionResult(Status.SUCCESS);
            actionMetrics.reportActionResult(Status.TIMEOUT);
            actionMetrics.reportRejectionAction(RejectionReason.CIRCUIT_OPEN);
            actionMetrics.reportRejectionAction(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
            actionMetrics.reportRejectionAction(RejectionReason.QUEUE_FULL);
        }

        when(systemTime.currentTimeMillis()).thenReturn(2000L, 2000L, 2000L, 2000L, 2000L, 2000L);
        assertEquals(3, actionMetrics.getSuccessesForTimePeriod(2000));
        assertEquals(2, actionMetrics.getSuccessesForTimePeriod(1000));
        assertEquals(3, actionMetrics.getErrorsForTimePeriod(2000));
        assertEquals(2, actionMetrics.getErrorsForTimePeriod(1000));
        assertEquals(3, actionMetrics.getTimeoutsForTimePeriod(2000));
        assertEquals(2, actionMetrics.getTimeoutsForTimePeriod(1000));
        assertEquals(3, actionMetrics.getMaxConcurrencyRejectionsForTimePeriod(2000));
        assertEquals(2, actionMetrics.getMaxConcurrencyRejectionsForTimePeriod(1000));
        assertEquals(3, actionMetrics.getQueueFullRejectionsForTimePeriod(2000));
        assertEquals(2, actionMetrics.getQueueFullRejectionsForTimePeriod(1000));
        assertEquals(3, actionMetrics.getCircuitOpenedRejectionsForTimePeriod(2000));
        assertEquals(2, actionMetrics.getCircuitOpenedRejectionsForTimePeriod(1000));
    }

    @Test
    public void testWrappingOfBuffer() {
        for (int i = 1; i < 1000; ++i) {
            long timestamp = 1000L * i;
            when(systemTime.currentTimeMillis()).thenReturn(timestamp, timestamp, timestamp);
            actionMetrics.reportActionResult(Status.ERROR);
            actionMetrics.reportActionResult(Status.SUCCESS);
            actionMetrics.reportActionResult(Status.TIMEOUT);
            actionMetrics.reportRejectionAction(RejectionReason.CIRCUIT_OPEN);
            actionMetrics.reportRejectionAction(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
            actionMetrics.reportRejectionAction(RejectionReason.QUEUE_FULL);
        }

        when(systemTime.currentTimeMillis()).thenReturn(1005000L);
        actionMetrics.reportActionResult(Status.ERROR);
        actionMetrics.reportActionResult(Status.SUCCESS);
        actionMetrics.reportActionResult(Status.TIMEOUT);
        actionMetrics.reportRejectionAction(RejectionReason.CIRCUIT_OPEN);
        actionMetrics.reportRejectionAction(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
        actionMetrics.reportRejectionAction(RejectionReason.QUEUE_FULL);

        assertEquals(2, actionMetrics.getSuccessesForTimePeriod(6000));
        assertEquals(2, actionMetrics.getErrorsForTimePeriod(6000));
        assertEquals(2, actionMetrics.getTimeoutsForTimePeriod(6000));
        assertEquals(4, actionMetrics.getFailuresForTimePeriod(6000));
        assertEquals(2, actionMetrics.getMaxConcurrencyRejectionsForTimePeriod(6000));
        assertEquals(2, actionMetrics.getQueueFullRejectionsForTimePeriod(6000));
        assertEquals(2, actionMetrics.getCircuitOpenedRejectionsForTimePeriod(6000));

    }
}
