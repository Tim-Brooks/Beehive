package fault.java;

import fault.java.singlewriter.ResilientPromise;
import fault.java.singlewriter.SingleWriterServiceExecutor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * Created by timbrooks on 11/16/14.
 */
public class ExampleRequest implements Runnable {

    private final SingleWriterServiceExecutor serviceExecutor;

    public ExampleRequest(SingleWriterServiceExecutor serviceExecutor) {
        this.serviceExecutor = serviceExecutor;
    }

    public void run() {
        ResilientPromise<String> result = serviceExecutor.performAction(new ResilientAction<String>() {
            @Override
            public String run() throws Exception {
                String result = null;
                InputStream response = new URL("http://localhost:6001/").openStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(response));
                result = reader.readLine();
                return result;
            }
        }, 10);

        long start = System.nanoTime();
        try {
            result.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long end = System.nanoTime();
        System.out.println((end - start) / 1000);
        System.out.println("Result: " + result.result);
        if (result.isError()) {
            System.out.println(result.error.getMessage());
        }
        System.out.println("Is Done " + result.isDone());
    }
}
