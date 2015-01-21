package fault;

import java.util.Map;

/**
 * Created by timbrooks on 1/20/15.
 */
public class HttpKitRequest<T> implements ResilientAction {

    public final Map<Object, Object> requestConfigs;

    public HttpKitRequest(Map<Object, Object> configs) {
        requestConfigs = configs;
    }


    @Override
    public T run() throws Exception {
        return null;
    }
}
