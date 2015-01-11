package fault;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ExampleRequest implements Runnable {

    private final ServiceExecutor serviceExecutor;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ExampleRequest(ServiceExecutor serviceExecutor) {
        this.serviceExecutor = serviceExecutor;
    }

    public void run() {
        for (; ; ) {
            List<ResilientFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < 1; ++i) {
                try {
                    ResilientFuture<String> result = serviceExecutor.performAction(new ResilientAction<String>() {
                        @Override
                        public String run() throws Exception {
                            new Random().nextBoolean();
//                            Thread.sleep(3);
                            String result = null;
                            InputStream response = new URL("http://localhost:6001/").openStream();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(response));
                            result = reader.readLine();
                            reader.close();
                            response.close();
                            return result;
                        }
                    }, 10);
                    futures.add(result);
                } catch (RuntimeException e) {
//                    System.out.println("Broken");
                }
            }

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
            for (ResilientFuture<String> result : futures) {
                try {
                    result.get();
//                Thread.sleep(1);
//            result.get(10L, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
//            e.printStackTrace();
                }
            }

//            if (result.status != Status.SUCCESS) {
//                System.out.println(System.currentTimeMillis() - start);
//                System.out.println(result.error);
//            }
//        if (result.isError()) {
//            System.out.println(result.error.getMessage());
//        }
//        System.out.println("Is Done " + result.isDone());
        }
    }
}
