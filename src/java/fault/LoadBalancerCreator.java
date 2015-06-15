package fault;

import java.util.Map;

/**
 * Created by timbrooks on 6/15/15.
 */
public class LoadBalancerCreator {

    public static <C> Pattern<C> roundRobin(Map<ServiceExecutor, C> executorToContext) {
        return new LoadBalancer<>(new RoundRobinStrategy(executorToContext.size()), executorToContext);
    }
}
