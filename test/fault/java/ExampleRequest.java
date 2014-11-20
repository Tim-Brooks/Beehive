package fault.java;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.*;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ExampleRequest implements Runnable {

    private final ServiceExecutor serviceExecutor;
    private final ExecutorService executorService;

    public ExampleRequest(ServiceExecutor serviceExecutor, ExecutorService executorService) {
        this.serviceExecutor = serviceExecutor;
        this.executorService = executorService;
    }

    public void run() {
        ResilientPromise<String> result = serviceExecutor.performAction(new ResilientAction<String>() {
            @Override
            public String run() throws Exception {
                Thread.sleep(10000);
                String result = null;
                InputStream response = new URL("http://localhost:6001/").openStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(response));
                result = reader.readLine();
                return result;
            }
        }, 805);

//        Future<Object> result = executorService.submit(new Callable<Object>() {
//            @Override
//            public Object call() throws Exception {
//                String result = null;
//                InputStream response = new URL("http://localhost:6001/").openStream();
//                BufferedReader reader = new BufferedReader(new InputStreamReader(response));
//                result = reader.readLine();
//                return result;
//            }
//        });

        long start = System.currentTimeMillis();
        try {
            result.await();
//            result.get(10L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
//            e.printStackTrace();
        }

        long end = System.currentTimeMillis();
//        System.out.println(end - start);
//        System.out.println("Result: " + result.result);
//        if (result.isError()) {
//            System.out.println(result.error.getMessage());
//        }
//        System.out.println("Is Done " + result.isDone());
    }
}
