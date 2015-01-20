package fault;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
* Created by timbrooks on 1/19/15.
*/
class ServiceThreadFactory implements ThreadFactory {

    private String name;
    public final AtomicInteger count = new AtomicInteger(0);

    public ServiceThreadFactory(String name) {
        this.name = name;
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, name + "-" + count.getAndIncrement());
    }
}
