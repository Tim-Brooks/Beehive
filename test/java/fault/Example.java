package fault;

import fault.circuit.BreakerConfig;
import fault.circuit.DefaultCircuitBreaker;
import fault.metrics.ActionMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Created by timbrooks on 11/6/14.
 */
public class Example {

    public static void main(String[] args) {
        ActionMetrics actionMetrics = new ActionMetrics(3600);
        BreakerConfig breakerConfig = new BreakerConfig.BreakerConfigBuilder().timePeriodInMillis(5000)
                .failureThreshold(100).timeToPauseMillis(2000).build();
        ServiceExecutor serviceExecutor = new ServiceExecutor(15, actionMetrics, new DefaultCircuitBreaker
                (actionMetrics, breakerConfig), Executors.newFixedThreadPool(15));
        BreakerConfig breakerConfig2 = new BreakerConfig.BreakerConfigBuilder().timePeriodInMillis(5000)
                .failureThreshold(100).timeToPauseMillis(2000).build();
        ActionMetrics actionMetrics2 = new ActionMetrics(3600);
        ServiceExecutor serviceExecutor2 = new ServiceExecutor(15, actionMetrics2, new DefaultCircuitBreaker
                (actionMetrics2, breakerConfig2), Executors.newFixedThreadPool(15));
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
        for (int i = 0; i < 3; ++i) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Thread thread = new Thread(new ExampleRequest(serviceExecutor2));
            threads.add(thread);
            thread.start();
        }
        try {
            for (int i = 0; i < 1000; ++i) {
                Thread.sleep(1000);
                System.out.println("Success " + actionMetrics.getSuccessesForTimePeriod(5000));
                System.out.println("Failures " + actionMetrics.getFailuresForTimePeriod(5000));
                System.out.println("Success " + actionMetrics2.getSuccessesForTimePeriod(5000));
                System.out.println("Failures " + actionMetrics2.getFailuresForTimePeriod(5000));
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
