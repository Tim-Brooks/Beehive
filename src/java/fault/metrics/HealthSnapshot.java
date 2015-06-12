package fault.metrics;

/**
 * Created by timbrooks on 6/11/15.
 */
public class HealthSnapshot {
    private final long total;
    private final long failures;
    private final long rejections;

    public HealthSnapshot(long total, long failures, long rejections) {
        this.total = total;
        this.failures = failures;
        this.rejections = rejections;
    }

    public double failurePercentage() {
        return failures / total;
    }

    public double rejectionPercentage() {
        return rejections / total;
    }
}
