package fault;

import fault.circuit.ICircuitBreaker;
import fault.messages.ResultMessage;
import fault.messages.ScheduleMessage;
import fault.metrics.IActionMetrics;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.*;



/**
 * Created by timbrooks on 12/12/14.
 */
public class ScheduleLoopTest {

    public final int poolSize = 2;
    @Mock
    public ICircuitBreaker circuitBreaker;
    @Mock
    public IActionMetrics actionMetrics;
    @Mock
    public ConcurrentLinkedQueue<ScheduleMessage<Object>> toScheduleQueue;
    @Mock
    public ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue;
    @Mock
    public ExecutorService executorService;
    @Mock
    public Map<ResultMessage<Object>, ResilientTask<Object>> taskMap;
    @Mock
    public SortedMap<Long, List<ResultMessage<Object>>> scheduled;
    @Captor
    public ArgumentCaptor<ResilientTask<Object>> taskCaptor;
    @Mock
    ResilientAction<Object> action;
    @Mock
    ResilientPromise<Object> promise;
    private ScheduleContext context;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        context = new ScheduleContext(poolSize, circuitBreaker, actionMetrics, toScheduleQueue, toReturnQueue,
                executorService, scheduled, taskMap);
    }

    @Test
    public void testLoop() {
        ScheduleMessage<Object> scheduleMessage = new ScheduleMessage<>(action, promise, 100L);
        when(toScheduleQueue.poll()).thenReturn(scheduleMessage, null);
        ScheduleLoop.runLoop(context);

        verify(executorService).submit(taskCaptor.capture());

        assertEquals(promise, taskCaptor.getValue().resilientPromise);

    }
}
