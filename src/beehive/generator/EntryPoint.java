package beehive.generator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EntryPoint {

    public static void main(String[] args) throws IOException {
        Map<String, Boolean> enumToFailed = new HashMap<>();
        enumToFailed.put("SUCCESS_S", false);
        enumToFailed.put("ERROR_F", true);
        EnumBuilder.buildResultEnum(enumToFailed);
    }
}
