package net.uncontended.fault;

import net.uncontended.fault.circuit.CircuitBreaker;
import net.uncontended.fault.metrics.ActionMetrics;

/**
 * Created by timbrooks on 12/23/14.
 */
public abstract class AbstractServiceExecutor implements ServiceExecutor {
    final ActionMetrics actionMetrics;
    final CircuitBreaker circuitBreaker;

    public AbstractServiceExecutor(CircuitBreaker circuitBreaker, ActionMetrics actionMetrics) {
        this.circuitBreaker = circuitBreaker;
        this.actionMetrics = actionMetrics;
    }

    public ActionMetrics getActionMetrics() {
        return actionMetrics;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

}
