package net.uncontended.fault;

/**
 * Created by timbrooks on 6/11/15.
 */
public interface LoadBalancerStrategy {

    int nextExecutorIndex();
}
