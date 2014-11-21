package fault.java.concurrent;

import java.util.Queue;

/**
 * Created by timbrooks on 11/11/14.
 */
public class ExchangingQueue<T> {

    private final T [] queue;
    private int head = 0;
    private int tail = 0;

    @SuppressWarnings("unchecked")
    public ExchangingQueue(int capacity) {
        this.queue = (T []) new Object[capacity];
    }

    public boolean offer(T element) {
        queue[tail] = element;
        ++tail;
        return true;
    }

    public T poll() {
        T element = queue[head];
        if (element != null) {
            ++head;
            return element;
        }
        return null;

    }

}
