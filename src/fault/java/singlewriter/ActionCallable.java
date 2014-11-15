package fault.java.singlewriter;

import fault.java.ResilientAction;
import fault.java.circuit.ResilientResult;

import java.util.concurrent.Callable;

/**
 * Created by timbrooks on 11/14/14.
 */
public class ActionCallable<T> implements Runnable {

    private final ResilientAction<T> action;
    private final ResilientResult<T> result;

    public ActionCallable(ResilientAction<T> action, ResilientResult<T> result) {
        this.action = action;
        this.result = result;

    }

    @Override
    public void run()  {
        boolean statusSetForFirstTime = false;
        try {
            T value = action.run();
            result.deliverResult(value);
        } catch (Exception e) {
            result.deliverError(e);
        }
    }
}
