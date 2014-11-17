package fault.java;

import fault.java.singlewriter.ResilientPromise;
import fault.java.singlewriter.SingleWriterServiceExecutor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * Created by timbrooks on 11/6/14.
 */
public class Example {

    public static void main(String[] args) {
        SingleWriterServiceExecutor serviceExecutor = new SingleWriterServiceExecutor(10);

        for (int i = 0; i < 50; ++i) {
            new Thread(new ExampleRequest(serviceExecutor)).start();
        }
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        serviceExecutor.shutdown();
    }

}
