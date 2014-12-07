package fault;

import fault.ManagingRunnable;
import fault.ResilientAction;
import fault.ResilientPromise;
import fault.ServiceExecutor;
import fault.circuit.ICircuitBreaker;
import fault.messages.ScheduleMessage;
import fault.metrics.IActionMetrics;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Created by timbrooks on 11/20/14.
 */
public class ServiceExecutorTest {

    @Mock
    private ICircuitBreaker circuitBreaker;
    @Mock
    private IActionMetrics actionMetrics;
    @Mock
    private ResilientAction<Object> resilientAction;
    @Mock
    private ManagingRunnable managingRunnable;
    @Captor
    private ArgumentCaptor<ScheduleMessage<Object>> messageCaptor;

    private ServiceExecutor serviceExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doNothing().when(managingRunnable).run();
        serviceExecutor = new ServiceExecutor(actionMetrics, circuitBreaker, managingRunnable);
    }

    @Test
    public void testExceptionThrownIfCircuitIsOpen() {
        when(circuitBreaker.isOpen()).thenReturn(true);
        try {
            serviceExecutor.performAction(resilientAction, 1000);
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Circuit is Open");
            return;
        }
        fail();
    }

    @Test
    public void testScheduleActionIsSubmittedIfCircuitIsClosed() {
        when(circuitBreaker.isOpen()).thenReturn(false);
        ResilientPromise<Object> resilientPromise = serviceExecutor.performAction(resilientAction, 1000);

        verify(managingRunnable).submit(messageCaptor.capture());

        ScheduleMessage scheduleMessage = messageCaptor.getValue();
        assertEquals(resilientAction, scheduleMessage.action);
        assertEquals(resilientPromise, scheduleMessage.promise);
    }

    @Test
    public void testShutdownCallsShutdownOnRunnable() {
        serviceExecutor.shutdown();

        verify(managingRunnable).shutdown();
    }
}
