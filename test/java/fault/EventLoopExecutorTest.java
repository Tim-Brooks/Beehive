package fault;

import fault.circuit.CircuitBreaker;
import fault.messages.ScheduleMessage;
import fault.metrics.ActionMetrics;
import fault.scheduling.ScheduleContext;
import fault.scheduling.Scheduler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Created by timbrooks on 11/20/14.
 */
public class EventLoopExecutorTest {

    @Mock
    private CircuitBreaker circuitBreaker;
    @Mock
    private ActionMetrics actionMetrics;
    @Mock
    private ResilientAction<Object> resilientAction;
    @Mock
    private ConcurrentLinkedQueue<ScheduleMessage<Object>> toSchedule;
    @Mock
    private Scheduler scheduler;
    @Mock
    private ExecutorService executorService;
    @Captor
    private ArgumentCaptor<ScheduleMessage<Object>> messageCaptor;

    private ScheduleContext context;
    private ServiceExecutor serviceExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        context = new ScheduleContext.ScheduleContextBuilder().setPoolSize(1).setExecutorService(executorService)
                .setActionMetrics(actionMetrics).setCircuitBreaker(circuitBreaker).setToScheduleQueue(toSchedule)
                .build();

        serviceExecutor = new EventLoopExecutor(actionMetrics, circuitBreaker, context, scheduler);
    }

    @Test
    public void testExceptionThrownIfCircuitDisallowsAction() {
        when(circuitBreaker.allowAction()).thenReturn(false);
        try {
            serviceExecutor.submitAction(resilientAction, 1000);
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(), "Circuit is Open");
            return;
        }
        fail();
    }

    @Test
    public void testScheduleActionIsSubmittedIfCircuitAllowsRequest() {
        when(circuitBreaker.allowAction()).thenReturn(true);
        ResilientFuture<Object> future = serviceExecutor.submitAction(resilientAction, 1000);

        verify(toSchedule).offer(messageCaptor.capture());

        ScheduleMessage scheduleMessage = messageCaptor.getValue();
        assertEquals(resilientAction, scheduleMessage.action);
    }

    @Test
    public void testShutdownUnschedulesService() {
        serviceExecutor.shutdown();

        verify(scheduler).unscheduleServiceExecutor(context);
        verify(executorService).shutdown();
    }
}
