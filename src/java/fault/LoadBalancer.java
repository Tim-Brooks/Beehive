package fault;

import fault.concurrent.DefaultResilientPromise;
import fault.concurrent.ResilientFuture;
import fault.concurrent.ResilientPromise;
import fault.utils.ResilientPatternAction;

import java.util.Map;

/**
 * Created by timbrooks on 6/4/15.
 */
public class LoadBalancer<C> implements Pattern<C> {

    private final ServiceExecutor[] services;
    private final C[] contexts;
    private final LoadBalancerStrategy strategy;

    @SuppressWarnings("unchecked")
    public LoadBalancer(LoadBalancerStrategy strategy, Map<ServiceExecutor, C> executorToContext) {
        if (executorToContext.size() == 0) {
            throw new IllegalArgumentException("Cannot create LoadBalancer with 0 Executors.");
        }

        this.strategy = strategy;
        services = new ServiceExecutor[executorToContext.size()];
        contexts = (C[]) new Object[executorToContext.size()];
        int i = 0;
        for (Map.Entry<ServiceExecutor, C> entry : executorToContext.entrySet()) {
            services[i] = entry.getKey();
            contexts[i] = entry.getValue();
            ++i;
        }
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
        final int i = strategy.nextExectutorIndex();
        return services[i].submitAction(new ResilientAction<T>() {
            @Override
            public T run() throws Exception {
                return action.run(contexts[i]);
            }
        }, promise, callback, millisTimeout);
    }

    @Override
    public <T> ResilientPromise<T> performAction(final ResilientPatternAction<T, C> action) {
        final int i = strategy.nextExectutorIndex();
        return services[i].performAction(new ResilientAction<T>() {
            @Override
            public T run() throws Exception {
                return action.run(contexts[i]);
            }
        });
    }

    @Override
    public void shutdown() {
        for (ServiceExecutor e : services) {
            e.shutdown();
        }
    }
}
