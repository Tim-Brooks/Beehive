package fault;

import fault.circuit.NoOpCircuitBreaker;
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
        ServiceExecutor serviceExecutor = new ServiceExecutor(50, actionMetrics, new NoOpCircuitBreaker(), Executors
                .newFixedThreadPool(50));
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 15; ++i) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Thread thread = new Thread(new ExampleRequest(serviceExecutor));
            threads.add(thread);
            thread.start();
        }
        try {
            for (int i = 0; i < 1000; ++i) {
                Thread.sleep(1000);
                System.out.println("Success " + actionMetrics.getSuccessesForTimePeriod(5000));
                System.out.println("Failures " + actionMetrics.getFailuresForTimePeriod(5000));
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
