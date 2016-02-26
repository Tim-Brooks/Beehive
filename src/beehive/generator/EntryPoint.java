package beehive.generator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EntryPoint {

    public static void main(String[] args) throws IOException {
        Map<String, Boolean> enumToFailed = new HashMap<>();
        enumToFailed.put("SUCCESS", false);
        enumToFailed.put("ERROR", true);
        EnumBuilder.buildResultEnum(enumToFailed);
    }
}
