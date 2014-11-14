package fault.java.singlewriter;

import fault.java.ActionMetrics;
import fault.java.ResilientAction;
import fault.java.TimeoutService;
import fault.java.circuit.BreakerConfig;
import fault.java.circuit.CircuitBreaker;
import fault.java.circuit.CircuitBreakerImplementation;
import fault.java.circuit.ResilientResult;

import java.util.concurrent.*;

/**
 * Created by timbrooks on 11/13/14.
 */
public class SingleWriterServiceExecutor {

    private final CircuitBreaker circuitBreaker;
    private final ActionMetrics actionMetrics;
    private final TimeoutService timeoutService;
    private final ConcurrentLinkedQueue<ResilientAction<?>> toScheduleQueue;
    private Thread managingThread;

    public SingleWriterServiceExecutor(int poolSize) {
        this.actionMetrics = new ActionMetrics();
        this.circuitBreaker = new CircuitBreakerImplementation(actionMetrics, new BreakerConfig());
        this.timeoutService = new TimeoutService();
        this.toScheduleQueue = new ConcurrentLinkedQueue<>();
    }

    public <T> ResilientResult<T> performAction(final ResilientAction<T> action, int millisTimeout) {
        if (circuitBreaker.isOpen()) {
            throw new RuntimeException("Circuit is Open");
        }
        final ResilientResult<T> resilientResult = new ResilientResult<>();
        Callable<Void> callable = new Callable<Void>() {
            @Override
            public Void call() {
                boolean statusSetForFirstTime = false;
                try {
                    T result = action.run();
                    statusSetForFirstTime = resilientResult.deliverResult(result);
                } catch (Exception e) {
                    statusSetForFirstTime = resilientResult.deliverError(e);
                } finally {
                    if (statusSetForFirstTime) {
                        actionMetrics.informActionOfResult(resilientResult);
                    }
                    circuitBreaker.informBreakerOfResult(resilientResult.isSuccessful());
                }
                return null;
            }
        };

//        timeoutService.scheduleTimeout(millisTimeout, resilientResult, scheduledFuture, actionMetrics);
        return resilientResult;
    }

    public void shutdown() {
        timeoutService.shutdown();
    }

    private void startManagerThread() {
        // TODO: Name thread.
        managingThread = new Thread(new ManagingRunnable(toScheduleQueue));
        managingThread.start();
    }
}
