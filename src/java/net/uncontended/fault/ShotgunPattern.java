package net.uncontended.fault;

import net.uncontended.fault.concurrent.ResilientFuture;
import net.uncontended.fault.concurrent.ResilientPromise;

import java.util.Map;

/**
 * Created by timbrooks on 6/16/15.
 */
public class ShotgunPattern<C> implements Pattern<C> {

    private final ServiceExecutor[] services;
    private final C[] contexts;

    @SuppressWarnings("unchecked")
    public ShotgunPattern(Map<ServiceExecutor, C> executorToContext) {
        if (executorToContext.size() == 0) {
            throw new IllegalArgumentException("Cannot create LoadBalancer with 0 Executors.");
        }

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
        return null;
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientCallback<T> callback,
                                               long millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise,
                                               long millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientFuture<T> submitAction(ResilientPatternAction<T, C> action, ResilientPromise<T> promise,
                                               ResilientCallback<T> callback, long millisTimeout) {
        return null;
    }

    @Override
    public <T> ResilientPromise<T> performAction(ResilientPatternAction<T, C> action) {
        return null;
    }

    @Override
    public void shutdown() {

    }
}
