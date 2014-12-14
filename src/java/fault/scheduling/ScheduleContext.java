package fault.scheduling;

import fault.ResilientTask;
import fault.circuit.ICircuitBreaker;
import fault.messages.ResultMessage;
import fault.messages.ScheduleMessage;
import fault.metrics.IActionMetrics;
import fault.utils.TimeProvider;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

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

    public static class ScheduleContextBuilder {
        private int poolSize;
        private ICircuitBreaker circuitBreaker;
        private IActionMetrics actionMetrics;
        private ExecutorService executorService;
        private ConcurrentLinkedQueue<ScheduleMessage<Object>> toScheduleQueue = new ConcurrentLinkedQueue<>();
        private ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue = new ConcurrentLinkedQueue<>();
        private SortedMap<Long, List<ResultMessage<Object>>> scheduled = new TreeMap<>();
        private Map<ResultMessage<Object>, ResilientTask<Object>> taskMap = new HashMap<>();
        private TimeProvider timeProvider = new TimeProvider();

        public ScheduleContextBuilder setPoolSize(int poolSize) {
            this.poolSize = poolSize;
            return this;
        }

        public ScheduleContextBuilder setCircuitBreaker(ICircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public ScheduleContextBuilder setActionMetrics(IActionMetrics actionMetrics) {
            this.actionMetrics = actionMetrics;
            return this;
        }

        public ScheduleContextBuilder setToScheduleQueue(ConcurrentLinkedQueue<ScheduleMessage<Object>>
                                                                 toScheduleQueue) {
            this.toScheduleQueue = toScheduleQueue;
            return this;
        }

        public ScheduleContextBuilder setToReturnQueue(ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue) {
            this.toReturnQueue = toReturnQueue;
            return this;
        }

        public ScheduleContextBuilder setExecutorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public ScheduleContextBuilder setScheduled(SortedMap<Long, List<ResultMessage<Object>>> scheduled) {
            this.scheduled = scheduled;
            return this;
        }

        public ScheduleContextBuilder setTaskMap(Map<ResultMessage<Object>, ResilientTask<Object>> taskMap) {
            this.taskMap = taskMap;
            return this;
        }

        public ScheduleContextBuilder setTimeProvider(TimeProvider timeProvider) {
            this.timeProvider = timeProvider;
            return this;
        }

        public ScheduleContext build() {
            return new ScheduleContext(poolSize, circuitBreaker, actionMetrics, toScheduleQueue, toReturnQueue,
                    executorService, scheduled, taskMap, timeProvider);
        }
    }
}
