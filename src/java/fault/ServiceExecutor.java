package fault;

import fault.circuit.BreakerConfig;
import fault.circuit.DefaultCircuitBreaker;
import fault.circuit.ICircuitBreaker;
import fault.messages.ResultMessage;
import fault.messages.ScheduleMessage;
import fault.metrics.ActionMetrics;
import fault.metrics.IActionMetrics;
import fault.scheduling.MultiplexingScheduler;
import fault.scheduling.ScheduleContext;
import fault.scheduling.Scheduler;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by timbrooks on 11/13/14.
 */
public class ServiceExecutor {

    private final IActionMetrics actionMetrics;

    private final ICircuitBreaker circuitBreaker;
    private final Scheduler scheduler;
    private final ScheduleContext schedulingContext;

    public ServiceExecutor(int poolSize) {
        this(poolSize, new ActionMetrics(3600));
    }

    public ServiceExecutor(int poolSize, IActionMetrics actionMetrics) {
        this(poolSize, actionMetrics, new DefaultCircuitBreaker(actionMetrics, new BreakerConfig.BreakerConfigBuilder
                ().failureThreshold(20).timePeriodInMillis(5000).build()), Executors.newFixedThreadPool(poolSize));
    }

    public ServiceExecutor(int poolSize, IActionMetrics actionMetrics, ICircuitBreaker circuitBreaker,
                           ExecutorService executorService) {
        this(actionMetrics, circuitBreaker, new ScheduleContext.ScheduleContextBuilder().setPoolSize(poolSize)
                .setActionMetrics(actionMetrics).setCircuitBreaker(circuitBreaker).setExecutorService
                        (executorService).build(), Scheduler.defaultScheduler);
    }

    public ServiceExecutor(IActionMetrics actionMetrics, ICircuitBreaker circuitBreaker, ScheduleContext
            scheduleContext, Scheduler scheduler) {
        this.scheduler = scheduler;
        this.actionMetrics = actionMetrics;
        this.circuitBreaker = circuitBreaker;
        this.schedulingContext = scheduleContext;
        this.scheduler.scheduleServiceExecutor(scheduleContext);
    }

    public <T> ResilientPromise<T> performAction(ResilientAction<T> action, int millisTimeout) {
        if (!circuitBreaker.allowAction()) {
            throw new RuntimeException("Circuit is Open");
        }
        long absoluteTimeout = millisTimeout + 1 + schedulingContext.timeProvider.currentTimeMillis();
        final ResilientPromise<T> resilientPromise = new ResilientPromise<>();

        ScheduleMessage<T> e = new ScheduleMessage<>(action, resilientPromise, absoluteTimeout);
        schedulingContext.toScheduleQueue.offer((ScheduleMessage<Object>) e);
        return resilientPromise;
    }

    public <T> ResilientPromise<T> performSyncAction(ResilientAction<T> action) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }

        ResilientPromise<T> resilientPromise = new ResilientPromise<>();
        ResultMessage<Object> resultMessage = new ResultMessage<>(ResultMessage.Type.SYNC);
        ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue = schedulingContext.toReturnQueue;
        try {
            T result = action.run();
            resultMessage.setResult(result);
            toReturnQueue.add(resultMessage);
            resilientPromise.deliverResult(result);
        } catch (ActionTimeoutException e) {
            resultMessage.setException(e);
            toReturnQueue.add(resultMessage);
            resilientPromise.setTimedOut();
        } catch (Exception e) {
            resultMessage.setException(e);
            toReturnQueue.add(resultMessage);
            resilientPromise.deliverError(e);
        }

        return resilientPromise;
    }

    public IActionMetrics getActionMetrics() {
        return actionMetrics;
    }

    public void shutdown() {
        scheduler.unscheduleServiceExecutor(schedulingContext);
        schedulingContext.executorService.shutdown();
    }

}
