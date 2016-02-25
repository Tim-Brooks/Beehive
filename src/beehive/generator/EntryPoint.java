package beehive.generator;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by timbrooks on 2/17/16.
 */
public class EntryPoint {

    public static void main(String[] args) throws IOException {
        EnumBuilder.build(Arrays.asList("Hello", "Goodbye"));
        EnumBuilder.build(Arrays.asList("Goodbye", "Hello"));
        EnumBuilder.build(Arrays.asList("Fuck", "Hello"));
    }
}
