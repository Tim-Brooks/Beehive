package fault.java.singlewriter;

import fault.java.ResilientAction;
import fault.java.circuit.ResilientResult;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by timbrooks on 11/14/14.
 */
public class ActionCallable<T> implements Runnable {

    private final ResilientAction<T> action;
    private final ResilientResult<T> result;
    private final ConcurrentLinkedQueue<ResilientResult<?>> toReturnQueue;
    public final long relativeTimeout;

    public ActionCallable(ResilientAction<T> action, long relativeTimeout, ResilientResult<T> result,
                          ConcurrentLinkedQueue<ResilientResult<?>> toReturnQueue) {
        this.action = action;
        this.relativeTimeout = relativeTimeout;
        this.result = result;
        this.toReturnQueue = toReturnQueue;

    }

    @Override
    public void run() {
        try {
            T value = action.run();
            result.deliverResult(value);
        } catch (Exception e) {
            result.deliverError(e);
        } finally {
            toReturnQueue.offer(result);
        }
    }
}
