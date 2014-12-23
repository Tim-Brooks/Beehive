package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;

/**
 * Created by timbrooks on 12/23/14.
 */
public class AbstractServiceExecutor {
    protected final ActionMetrics actionMetrics;
    protected final CircuitBreaker circuitBreaker;

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
