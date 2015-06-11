package fault;

import fault.utils.ResilientPatternAction;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by timbrooks on 6/11/15.
 */
public class LoadBalancerTest {

    @Mock
    private ServiceExecutor executor1;
    @Mock
    private ServiceExecutor executor2;
    @Mock
    private LoadBalancerStrategy strategy;
    @Mock
    private ResilientPatternAction<String, Map<String, Object>> action;
    @Captor
    private ArgumentCaptor<ResilientAction<String>> actionCaptor;

    private Map<String, Object> context1;
    private Map<String, Object> context2;
    private LoadBalancer<Map<String, Object>> balancer;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        context1 = new HashMap<>();
        context1.put("port", 8000);
        context2 = new HashMap<>();
        context2.put("host", 8001);
        Map<ServiceExecutor, Map<String, Object>> map = new LinkedHashMap<>();
        map.put(executor1, context1);
        map.put(executor2, context2);

        balancer = new LoadBalancer<>(strategy, map);
    }

    @Test
    public void actionCalledWithCorrectContext() throws Exception {
        when(strategy.nextExectutorIndex()).thenReturn(0);
        balancer.performAction(action);
        verify(executor1).performAction(actionCaptor.capture());
        actionCaptor.getValue().run();
        verify(action).run(context1);

        when(strategy.nextExectutorIndex()).thenReturn(1);
        balancer.performAction(action);
        verify(executor2).performAction(actionCaptor.capture());
        actionCaptor.getValue().run();
        verify(action).run(context2);
    }

}
