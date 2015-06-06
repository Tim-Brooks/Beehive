package fault;

import fault.concurrent.DefaultResilientPromise;
import fault.concurrent.ResilientFuture;
import fault.concurrent.ResilientPromise;
import fault.utils.ResilientPatternAction;

import java.util.List;
import java.util.Random;

/**
 * Created by timbrooks on 6/4/15.
 */
public class LoadBalancer<C> implements Pattern<C> {

    private final ServiceExecutor[] services;
    private final C context;

    public LoadBalancer(List<ServiceExecutor> executors, C context) {
        if (executors.size() == 0) {
            throw new IllegalArgumentException("Cannot create LoadBalancer with 0 Executors.");
        }

        this.context = context;
        services = (ServiceExecutor[]) executors.toArray();
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, long millisTimeout) {
        return submitAction(action, new DefaultResilientPromise<T>(), null, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientCallback<T> callback,
                                               long millisTimeout) {
        return submitAction(action, new DefaultResilientPromise<T>(), callback, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise,
                                               long millisTimeout) {
        return submitAction(action, promise, null, millisTimeout);
    }

    @Override
    public <T> ResilientFuture<T> submitAction(final ResilientPatternAction<T, C> action, ResilientPromise<T> promise,
                                               ResilientCallback<T> callback, long millisTimeout) {
        return services[nextService()].submitAction(new ResilientAction<T>() {
            @Override
            public T run() throws Exception {
                return action.run(context);
            }
        }, promise, callback, millisTimeout);
    }

    @Override
    public <T> ResilientPromise<T> performAction(final ResilientPatternAction<T, C> action) {
        return services[nextService()].performAction(new ResilientAction<T>() {
            @Override
            public T run() throws Exception {
                return action.run(context);
            }
        });
    }

    private int nextService() {
        return new Random().nextInt(services.length) % services.length;
    }
}
