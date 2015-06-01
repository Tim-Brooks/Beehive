package fault.circuit;

import fault.metrics.ActionMetrics;
import fault.utils.SystemTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by timbrooks on 11/20/14.
 */
public class DefaultCircuitBreakerTest {

    @Mock
    private ActionMetrics actionMetrics;
    @Mock
    private SystemTime systemTime;

    private CircuitBreaker circuitBreaker;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCircuitIsClosedByDefault() {
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(20)
                .timePeriodInMillis(5000).build();
        circuitBreaker = new DefaultCircuitBreaker(actionMetrics, breakerConfig);
        assertFalse(circuitBreaker.isOpen());
    }

    @Test
    public void testCircuitOpensOnlyWhenFailuresGreaterThanThreshold() {
        int timePeriodInMillis = 1000;
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(5).timePeriodInMillis
                (timePeriodInMillis).build();
        circuitBreaker = new DefaultCircuitBreaker(actionMetrics, breakerConfig);

        assertFalse(circuitBreaker.isOpen());

        when(actionMetrics.getFailuresForTimePeriod(timePeriodInMillis)).thenReturn(5);
        circuitBreaker.informBreakerOfResult(false);
        assertFalse(circuitBreaker.isOpen());

        when(actionMetrics.getFailuresForTimePeriod(timePeriodInMillis)).thenReturn(6);
        circuitBreaker.informBreakerOfResult(false);

        assertTrue(circuitBreaker.isOpen());
    }

    @Test
    public void testOpenCircuitClosesAfterSuccess() {
        int timePeriodInMillis = 1000;
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(5).timePeriodInMillis
                (timePeriodInMillis).build();
        circuitBreaker = new DefaultCircuitBreaker(actionMetrics, breakerConfig);

        assertFalse(circuitBreaker.isOpen());

        when(actionMetrics.getFailuresForTimePeriod(timePeriodInMillis)).thenReturn(6);
        circuitBreaker.informBreakerOfResult(false);

        assertTrue(circuitBreaker.isOpen());

        circuitBreaker.informBreakerOfResult(true);

        assertFalse(circuitBreaker.isOpen());
    }

    @Test
    public void testSettingBreakerConfigChangesConfig() {
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(10).timePeriodInMillis
                (1000).build();
        circuitBreaker = new DefaultCircuitBreaker(actionMetrics, breakerConfig);
        when(actionMetrics.getFailuresForTimePeriod(1000)).thenReturn(6);

        circuitBreaker.informBreakerOfResult(false);

        verify(actionMetrics, times(1)).getFailuresForTimePeriod(1000);
        assertFalse(circuitBreaker.isOpen());

        BreakerConfig newBreakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(5)
                .timePeriodInMillis(2000).build();
        circuitBreaker.setBreakerConfig(newBreakerConfig);
        when(actionMetrics.getFailuresForTimePeriod(2000)).thenReturn(6);
        circuitBreaker.informBreakerOfResult(false);

        verify(actionMetrics, times(1)).getFailuresForTimePeriod(2000);
        assertTrue(circuitBreaker.isOpen());
    }

    @Test
    public void testActionAllowedIfCircuitClosed() {
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(20)
                .timePeriodInMillis(5000).build();
        circuitBreaker = new DefaultCircuitBreaker(actionMetrics, breakerConfig);
        assertFalse(circuitBreaker.isOpen());
        assertTrue(circuitBreaker.allowAction());
    }

    @Test
    public void testActionAllowedIfPauseTimeHasPassed() {
        final int failureThreshold = 10;
        int timePeriodInMillis = 5000;
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(failureThreshold)
                .timePeriodInMillis(timePeriodInMillis).timeToPauseMillis(1000).build();

        circuitBreaker = new DefaultCircuitBreaker(actionMetrics, breakerConfig, systemTime);

        assertFalse(circuitBreaker.isOpen());
        assertTrue(circuitBreaker.allowAction());

        when(actionMetrics.getFailuresForTimePeriod(timePeriodInMillis)).thenReturn(11);
        when(systemTime.currentTimeMillis()).thenReturn(0L);
        circuitBreaker.informBreakerOfResult(false);

        when(systemTime.currentTimeMillis()).thenReturn(999L);
        assertFalse(circuitBreaker.allowAction());
        assertTrue(circuitBreaker.isOpen());

        when(systemTime.currentTimeMillis()).thenReturn(1001L);
        assertTrue(circuitBreaker.allowAction());
        assertTrue(circuitBreaker.isOpen());

    }

    @Test
    public void testActionNotAllowedIfCircuitForcedOpen() {
        final int failureThreshold = 10;
        int timePeriodInMillis = 5000;
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().failureThreshold(failureThreshold)
                .timePeriodInMillis(timePeriodInMillis).timeToPauseMillis(1000).build();

        circuitBreaker = new DefaultCircuitBreaker(actionMetrics, breakerConfig, systemTime);

        assertFalse(circuitBreaker.isOpen());
        assertTrue(circuitBreaker.allowAction());

        circuitBreaker.forceOpen();

        when(systemTime.currentTimeMillis()).thenReturn(1001L);
        assertFalse(circuitBreaker.allowAction());
        assertTrue(circuitBreaker.isOpen());

        circuitBreaker.forceClosed();

        assertTrue(circuitBreaker.allowAction());
        assertFalse(circuitBreaker.isOpen());

    }

}
