package fault.java;

import fault.java.singlewriter.ResilientPromise;
import fault.java.singlewriter.SingleWriterServiceExecutor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.Executors;

/**
 * Created by timbrooks on 11/6/14.
 */
public class Example {

    public static void main(String[] args) {
        SingleWriterServiceExecutor serviceExecutor = new SingleWriterServiceExecutor(10);

        for (int i = 0; i < 50; ++i) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            new Thread(new ExampleRequest(serviceExecutor, Executors.newFixedThreadPool(20))).start();
        }
        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        serviceExecutor.shutdown();
    }

}
