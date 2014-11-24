package fault.java.metrics;

import org.junit.Before;
import org.junit.Test;

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
}
