package fault.concurrent;

import fault.Status;

/**
 * Created by timbrooks on 11/16/14.
 */
public class SingleWriterResilientPromise<T> extends AbstractResilientPromise<T> {
    @Override
    public boolean deliverResult(T result) {
        this.result = result;
        status.lazySet(Status.SUCCESS);
        latch.countDown();
        return true;
    }

    @Override
    public boolean deliverError(Throwable error) {
        this.error = error;
        status.lazySet(Status.ERROR);
        latch.countDown();
        return true;
    }

    @Override
    public boolean setTimedOut() {
        status.lazySet(Status.TIMED_OUT);
        latch.countDown();
        return true;
    }

}
