package fault;

import fault.circuit.ICircuitBreaker;
import fault.messages.ResultMessage;
import fault.messages.ScheduleMessage;
import fault.metrics.IActionMetrics;
import fault.utils.TimeProvider;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by timbrooks on 12/12/14.
 */
public class ScheduleContext {

    public final int poolSize;
    public final ICircuitBreaker circuitBreaker;
    public final IActionMetrics actionMetrics;
    public final ConcurrentLinkedQueue<ScheduleMessage<Object>> toScheduleQueue;
    public final ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue;
    public final ExecutorService executorService;
    public final Map<ResultMessage<Object>, ResilientTask<Object>> taskMap;
    public final TimeProvider timeProvider;
    public SortedMap<Long, List<ResultMessage<Object>>> scheduled;

    public ScheduleContext(int poolSize, ICircuitBreaker circuitBreaker, IActionMetrics actionMetrics) {
        this(poolSize, circuitBreaker, actionMetrics, new ConcurrentLinkedQueue<ScheduleMessage<Object>>(), new
                        ConcurrentLinkedQueue<ResultMessage<Object>>(),
                Executors.newFixedThreadPool(poolSize), new TreeMap<Long, List<ResultMessage<Object>>>(), new
                        HashMap<ResultMessage<Object>, ResilientTask<Object>>(), new TimeProvider());
    }

    public ScheduleContext(int poolSize, ICircuitBreaker circuitBreaker, IActionMetrics actionMetrics,
                           ConcurrentLinkedQueue<ScheduleMessage<Object>> toScheduleQueue,
                           ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue, ExecutorService
                                   executorService, SortedMap<Long, List<ResultMessage<Object>>> scheduled,
                           Map<ResultMessage<Object>, ResilientTask<Object>> taskMap, TimeProvider timeProvider) {
        this.poolSize = poolSize;
        this.circuitBreaker = circuitBreaker;
        this.actionMetrics = actionMetrics;
        this.toScheduleQueue = toScheduleQueue;
        this.toReturnQueue = toReturnQueue;
        this.executorService = executorService;
        this.scheduled = scheduled;
        this.taskMap = taskMap;
        this.timeProvider = timeProvider;
    }
}
