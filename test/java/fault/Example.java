package fault;

import fault.circuit.NoOpCircuitBreaker;
import fault.metrics.ActionMetrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timbrooks on 11/6/14.
 */
public class Example {

    public static void main(String[] args) {
        ActionMetrics actionMetrics = new ActionMetrics(3600);
        ServiceExecutor serviceExecutor = new ServiceExecutor(50, actionMetrics, new NoOpCircuitBreaker());
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
            Thread.sleep(120000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (Thread t : threads) {
            t.interrupt();
        }
        serviceExecutor.shutdown();
    }

}
