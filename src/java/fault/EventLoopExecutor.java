package fault;

import fault.circuit.BreakerConfig;
import fault.circuit.CircuitBreaker;
import fault.circuit.DefaultCircuitBreaker;
import fault.messages.ResultMessage;
import fault.messages.ScheduleMessage;
import fault.metrics.ActionMetrics;
import fault.metrics.SingleWriterActionMetrics;
import fault.scheduling.ScheduleContext;
import fault.scheduling.Scheduler;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by timbrooks on 11/13/14.
 */
public class EventLoopExecutor implements ServiceExecutor {

    private final ActionMetrics actionMetrics;
    private final CircuitBreaker circuitBreaker;
    private final Scheduler scheduler;
    private final ScheduleContext schedulingContext;

    public EventLoopExecutor(int poolSize) {
        this(poolSize, new SingleWriterActionMetrics(3600));
    }

    public EventLoopExecutor(int poolSize, ActionMetrics actionMetrics) {
        this(poolSize, actionMetrics, new DefaultCircuitBreaker(actionMetrics, new BreakerConfig.BreakerConfigBuilder
                ().failureThreshold(20).timePeriodInMillis(5000).build()), Executors.newFixedThreadPool(poolSize));
    }

    public EventLoopExecutor(int poolSize, ActionMetrics actionMetrics, CircuitBreaker circuitBreaker,
                             ExecutorService executorService) {
        this(actionMetrics, circuitBreaker, new ScheduleContext.ScheduleContextBuilder().setPoolSize(poolSize)
                .setActionMetrics(actionMetrics).setCircuitBreaker(circuitBreaker).setExecutorService
                        (executorService).build(), Scheduler.defaultScheduler);
    }

    public EventLoopExecutor(ActionMetrics actionMetrics, CircuitBreaker circuitBreaker, ScheduleContext
            scheduleContext, Scheduler scheduler) {
        this.scheduler = scheduler;
        this.actionMetrics = actionMetrics;
        this.circuitBreaker = circuitBreaker;
        this.schedulingContext = scheduleContext;
        this.scheduler.scheduleServiceExecutor(scheduleContext);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientAction<T> action, long millisTimeout) {
        return submitAction(action, new SingleWriterResilientPromise<T>(), millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(final ResilientAction<T> action, final ResilientPromise<T> promise,
                                               long millisTimeout) {
        if (!circuitBreaker.allowAction()) {
            throw new RejectedActionException(RejectedActionException.Reason.CIRCUIT_CLOSED);
        }
        long absoluteTimeout = millisTimeout + 1 + schedulingContext.timeProvider.currentTimeMillis();

        ScheduleMessage<T> e = new ScheduleMessage<>(action, promise, millisTimeout, absoluteTimeout);
        schedulingContext.toScheduleQueue.offer((ScheduleMessage<Object>) e);
        return new ResilientFuture<>(promise);
    }

    @Override
    public <T> ResilientPromise<T> performAction(ResilientAction<T> action) {
        if (circuitBreaker.isOpen()) {
            throw new RejectedActionException(RejectedActionException.Reason.CIRCUIT_CLOSED);
        }

        ResilientPromise<T> resilientPromise = new SingleWriterResilientPromise<>();
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

    @Override
    public ActionMetrics getActionMetrics() {
        return actionMetrics;
    }

    @Override
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    @Override
    public void shutdown() {
        scheduler.unscheduleServiceExecutor(schedulingContext);
        schedulingContext.executorService.shutdown();
    }

}
