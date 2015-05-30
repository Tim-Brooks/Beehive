package fault.scheduling;

import fault.ResilientAction;
import fault.messages.ResultMessage;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by timbrooks on 11/14/14.
 */
public class ActionCallable<T> implements Callable<Void> {

    public final ResultMessage<T> resultMessage;
    private final ResilientAction<T> action;
    private final ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue;

    public ActionCallable(ResilientAction<T> action, ConcurrentLinkedQueue<ResultMessage<Object>> toReturnQueue) {
        this.action = action;
        this.toReturnQueue = toReturnQueue;
        this.resultMessage = new ResultMessage<>(ResultMessage.Type.ASYNC);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Void call() {
        try {
            T value = action.run();
            resultMessage.setResult(value);
        } catch (Exception e) {
            resultMessage.setException(e);
        }
        toReturnQueue.offer((ResultMessage<Object>) resultMessage);
        return null;
    }
}
