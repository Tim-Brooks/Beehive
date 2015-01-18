package fault;

import fault.circuit.CircuitBreaker;
import fault.metrics.ActionMetrics;

import java.util.UUID;

/**
 * Created by timbrooks on 12/23/14.
 */
public abstract class AbstractServiceExecutor implements ServiceExecutor {
    protected final ActionMetrics actionMetrics;
    protected final CircuitBreaker circuitBreaker;
    protected final UUID uuid = UUID.randomUUID();

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

    public UUID getExecutorUUID() {
        return uuid;
    }
}
