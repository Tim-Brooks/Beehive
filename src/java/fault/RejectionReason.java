package fault;

/**
 * Created by timbrooks on 1/15/15.
 */
public enum RejectionReason {
    CIRCUIT_OPEN,
    QUEUE_FULL,
    MAX_CONCURRENCY_LEVEL_EXCEEDED,
    SERVICE_SHUTDOWN
}
