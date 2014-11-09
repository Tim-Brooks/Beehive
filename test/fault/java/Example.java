package fault.java;

import com.sun.tools.doclets.internal.toolkit.util.DocFinder;
import fault.java.circuit.ResilientResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by timbrooks on 11/6/14.
 */
public class Example {

    public static void main(String[] args) {
        ServiceExecutor serviceExecutor = new ServiceExecutor(5);
        ResilientResult<String> result = serviceExecutor.performAction(new ResilientAction<String>() {
            @Override
            public String run() throws Exception {
                String result = null;
                InputStream response = new URL("http://localhost:6001/").openStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(response));
                result = reader.readLine();
                return result;
            }
        }, 10);

        int i = 0;
        long start = System.nanoTime();
        while (!result.isDone()) {
            ++i;
        }
        long end = System.nanoTime();
        System.out.println((end - start) / 1000);
        System.out.println(i + result.result);
        if (result.isFailed()) {
            System.out.println(result.error.getMessage());
        }
        System.out.println(result.status);
        serviceExecutor.shutdown();
    }
}
