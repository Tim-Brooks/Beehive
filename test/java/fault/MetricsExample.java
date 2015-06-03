package fault;

import fault.metrics.Metric;
import fault.metrics.MultiWriterActionMetrics;
import fault.metrics.NewActionMetrics;

import java.util.concurrent.CountDownLatch;

/**
 * Created by timbrooks on 6/3/15.
 */
public class MetricsExample {

    public static void main(String[] args) {

        MultiWriterActionMetrics metrics = new MultiWriterActionMetrics(60);

//        CodahaleMetrics metrics = new CodahaleMetrics();
        try {
            for (int i = 0; i < 1000; ++i) {
                fireThreads(metrics, 10);
            }

            for (int i = 0; i < 100000; ++i) {
                long start = System.nanoTime();
                metrics.getMetricForTimePeriod(Metric.SUCCESS, 5);
                System.out.println(System.nanoTime() - start);
            }
        } catch (InterruptedException e) {
        }


    }

    private static void fireThreads(final NewActionMetrics metrics, int num) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(num);

        for (int i = 0; i < num; ++i) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < 100; ++j) {
                        metrics.incrementMetric(Metric.SUCCESS);
                        metrics.incrementMetric(Metric.ERROR);
                        metrics.incrementMetric(Metric.TIMEOUT);
                        metrics.incrementMetric(Metric.MAX_CONCURRENCY_LEVEL_EXCEEDED);
                        metrics.incrementMetric(Metric.QUEUE_FULL);
                        metrics.incrementMetric(Metric.CIRCUIT_OPEN);
                    }
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
    }
}
