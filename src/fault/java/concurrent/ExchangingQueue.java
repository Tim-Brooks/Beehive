package fault.java.concurrent;

import java.util.Queue;

/**
 * Created by timbrooks on 11/11/14.
 */
public class ExchangingQueue<E> {

    public boolean offer(E element) {
        return true;
    }

    public E poll() {
        return null;
    }

}
