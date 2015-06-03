package fault;

import fault.circuit.BreakerConfig;
import fault.circuit.DefaultCircuitBreaker;
import fault.metrics.ActionMetrics;
import fault.metrics.Metric;
import fault.metrics.MultiWriterActionMetrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timbrooks on 11/6/14.
 */
public class Example {

    public static void main(String[] args) {
        ActionMetrics actionMetrics = new MultiWriterActionMetrics(3600);
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().timePeriodInMillis(5000)
                .failureThreshold(100).timeToPauseMillis(2000).build();
//        EventLoopExecutor serviceExecutor = new EventLoopExecutor(15, actionMetrics, new DefaultCircuitBreaker
//                (actionMetrics, breakerConfig), Executors.newFixedThreadPool(15));
        BlockingExecutor serviceExecutor = new BlockingExecutor(25, 120, "Test", actionMetrics, new
                DefaultCircuitBreaker
                (actionMetrics, breakerConfig));
//        BreakerConfig breakerConfig2 = new BreakerConfig.BreakerConfigBuilder().timePeriodInMillis(5000)
//                .failureThreshold(400).timeToPauseMillis(2000).build();
//        SingleWriterActionMetrics actionMetrics2 = new SingleWriterActionMetrics(3600);
//        ServiceExecutor serviceExecutor2 = new EventLoopExecutor(15, actionMetrics2, new DefaultCircuitBreaker
//                (actionMetrics2, breakerConfig2), Executors.newFixedThreadPool(15));
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 3; ++i) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Thread thread = new Thread(new ExampleRequest(serviceExecutor));
            threads.add(thread);
            thread.start();
        }
//        for (int i = 0; i < 3; ++i) {
//            try {
//                Thread.sleep(30);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            Thread thread = new Thread(new ExampleRequest(serviceExecutor2));
//            threads.add(thread);
//            thread.start();
//        }
        try {
            for (int i = 0; i < 1000; ++i) {
                Thread.sleep(1000);
                System.out.println("Success " + actionMetrics.getMetricForTimePeriod(Metric.SUCCESS, 1));
                System.out.println("Failures " + actionMetrics.getMetricForTimePeriod(Metric.TIMEOUT, 1));
                System.out.println("Errors " + actionMetrics.getMetricForTimePeriod(Metric.ERROR, 1));
                System.out.println("Concurrency " + actionMetrics.getMetricForTimePeriod(Metric
                        .MAX_CONCURRENCY_LEVEL_EXCEEDED, 1));
                System.out.println("Circuit " + actionMetrics.getMetricForTimePeriod(Metric.CIRCUIT_OPEN, 1));
//                System.out.println("Success2 " + actionMetrics2.getSuccessesForTimePeriod(5000));
//                System.out.println("Failures2 " + actionMetrics2.getMetricForTimePeriod(5000));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (Thread t : threads) {
            t.interrupt();
        }
        serviceExecutor.shutdown();
    }

}
