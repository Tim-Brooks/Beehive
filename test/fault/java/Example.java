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
            public String run() {
                String result = null;
                try {
                    InputStream response = new URL("http://www.google.com").openStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(response));
                    result = reader.readLine();
                } catch (IOException e) {
                }
                return result;
            }
        });

        System.out.println(result.result);
    }
}
