package fault.java.metrics;

import fault.java.Status;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Created by timbrooks on 11/23/14.
 */
public class ActionMetricsTest {

    private ActionMetrics actionMetrics;

    @Before
    public void setUp() {
        this.actionMetrics = new ActionMetrics();
    }

    @Test
    public void testMetricsReportsNoErrorsIfNoErrorsOrTimeouts() {
        assertEquals(0, actionMetrics.getFailuresForTimePeriod(1000));
    }

    @Test
    public void testMetricsReportCorrectErrorCount() {
        int errorCount = new Random().nextInt(100);
        for (int i = 0; i < errorCount; ++i) {
            actionMetrics.reportActionResult(Status.ERROR);
        }
        assertEquals(errorCount, actionMetrics.getFailuresForTimePeriod(10000));

    }
}
