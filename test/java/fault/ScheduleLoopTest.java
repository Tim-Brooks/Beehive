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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Created by timbrooks on 12/12/14.
 */
public class ScheduleLoopTest {

    @Mock
    private ICircuitBreaker circuitBreaker;
    @Mock
    private IActionMetrics actionMetrics;
    @Mock
    private ConcurrentLinkedQueue<ScheduleMessage<Object>> toScheduleQueue;
    @Mock
    private ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue;
    @Mock
    private ExecutorService executorService;
    @Mock
    private Map<ResultMessage<Object>, ResilientTask<Object>> taskMap;
    @Mock
    private SortedMap<Long, List<ResultMessage<Object>>> scheduled;
    @Captor
    private ArgumentCaptor<ResilientTask<Object>> taskCaptor;
    @Mock
    private ResilientAction<Object> action;
    @Mock
    private ResilientAction<Object> action2;
    @Mock
    private ResilientPromise<Object> promise2;
    @Mock
    private ResilientPromise<Object> promise;

    private ScheduleContext context;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        setContext(2);
    }

    @Test
    public void testRunLoopReturnsFalseIfNothingDone() throws Exception {
        setContext(1);
        when(toScheduleQueue.poll()).thenReturn(null);
        when(toReturnQueue.poll()).thenReturn(null);

        assertFalse(ScheduleLoop.runLoop(context));
    }

    @Test
    public void testRunLoopReturnsTrueIfActionScheduled() throws Exception {
        setContext(1);
        when(toScheduleQueue.poll()).thenReturn(new ScheduleMessage<>(action, promise, 100L));
        when(toReturnQueue.poll()).thenReturn(null);

        assertTrue(ScheduleLoop.runLoop(context));
    }

    @Test
    public void testRunLoopReturnsTrueIfResultHandled() throws Exception {
        setContext(1);
        when(toScheduleQueue.poll()).thenReturn(null);
        when(toReturnQueue.poll()).thenReturn(new ResultMessage<Object>(ResultMessage.Type.ASYNC));

        assertTrue(ScheduleLoop.runLoop(context));

    }

    @Test
    public void testActionsScheduled() throws Exception {
        ScheduleMessage<Object> scheduleMessage = new ScheduleMessage<>(action, promise, 100L);
        ScheduleMessage<Object> scheduleMessage2 = new ScheduleMessage<>(action2, promise2, 101L);
        when(toScheduleQueue.poll()).thenReturn(scheduleMessage, scheduleMessage2);
        ScheduleLoop.runLoop(context);

        verify(executorService, times(2)).submit(taskCaptor.capture());

        List<ResilientTask<Object>> tasks = taskCaptor.getAllValues();

        ResilientTask<Object> task1 = tasks.get(0);
        assertEquals(promise, task1.resilientPromise);
        task1.run();
        verify(action).run();


        ResilientTask<Object> task2 = tasks.get(1);
        assertEquals(promise2, task2.resilientPromise);
        task2.run();
        verify(action2).run();

    }

    private void setContext(int threadNum) {
        context = new ScheduleContext(threadNum, circuitBreaker, actionMetrics, toScheduleQueue, toReturnQueue,
                executorService, scheduled, taskMap);
    }
}
