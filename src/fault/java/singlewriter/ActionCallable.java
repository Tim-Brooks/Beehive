package fault.java.singlewriter;

import fault.java.ResilientAction;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by timbrooks on 11/14/14.
 */
public class ActionCallable<T> implements Callable<Void> {

    public final ResultMessage<T> resultMessage;
    private final ResilientAction<T> action;
    private final ConcurrentLinkedQueue<ResultMessage<?>> toReturnQueue;

    public ActionCallable(ResilientAction<T> action, ConcurrentLinkedQueue<ResultMessage<?>> toReturnQueue) {
        this.action = action;
        this.toReturnQueue = toReturnQueue;
        this.resultMessage = new ResultMessage<>();
    }

    @Override
    public Void call() {
        try {
            T value = action.run();
            resultMessage.setResult(value);
        } catch (Exception e) {
            resultMessage.setException(e);
        }
        toReturnQueue.offer(resultMessage);
        return null;
    }
}
