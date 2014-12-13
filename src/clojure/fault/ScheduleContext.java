package fault;

import fault.circuit.ICircuitBreaker;
import fault.messages.ResultMessage;
import fault.messages.ScheduleMessage;
import fault.metrics.IActionMetrics;

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
    public SortedMap<Long, List<ResultMessage<Object>>> scheduled;

    public ScheduleContext(int poolSize, ICircuitBreaker circuitBreaker, IActionMetrics actionMetrics) {
        this.poolSize = poolSize;
        this.circuitBreaker = circuitBreaker;
        this.actionMetrics = actionMetrics;
        this.toScheduleQueue = new ConcurrentLinkedQueue<>();
        this.toReturnQueue = new ConcurrentLinkedQueue<>();
        this.executorService = Executors.newFixedThreadPool(poolSize);
        this.scheduled = new TreeMap<>();
        this.taskMap = new HashMap<>();
    }
}
