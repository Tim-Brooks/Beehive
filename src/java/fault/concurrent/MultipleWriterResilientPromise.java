package fault.concurrent;

import fault.Status;

/**
 * Created by timbrooks on 12/22/14.
 */
public class MultipleWriterResilientPromise<T> extends AbstractResilientPromise<T> {

    @Override
    public boolean deliverResult(T result) {
        if (status.get() == Status.PENDING) {
            if (status.compareAndSet(Status.PENDING, Status.SUCCESS)) {
                this.result = result;
                latch.countDown();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean deliverError(Throwable error) {
        if (status.get() == Status.PENDING) {
            if (status.compareAndSet(Status.PENDING, Status.ERROR)) {
                this.error = error;
                latch.countDown();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean setTimedOut() {
        if (status.get() == Status.PENDING) {
            if (status.compareAndSet(Status.PENDING, Status.TIMED_OUT)) {
                latch.countDown();
                return true;
            }
        }
        return false;
    }

}
