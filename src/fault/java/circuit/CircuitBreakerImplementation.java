package fault.java.circuit;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by timbrooks on 11/5/14.
 */
public class CircuitBreakerImplementation implements CircuitBreaker {

    private final AtomicBoolean fuse;

    public CircuitBreakerImplementation() {
       fuse = new AtomicBoolean(false);
    }

    @Override
    public boolean isOpen() {
        return fuse.get();
    }
}
