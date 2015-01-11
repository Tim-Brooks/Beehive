package fault;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * Created by timbrooks on 1/7/15.
 */
public class BlockingExecutorTest {

    private ServiceExecutor blockingExecutor;

    @Before
    public void setUp() {
        blockingExecutor = new BlockingExecutor(1, 1000);
    }

    @Test
    public void testTimeoutScheduled() throws Exception {
        ResilientFuture<String> future = blockingExecutor.submitAction(new ResilientAction<String>() {
            @Override
            public String run() throws Exception {
                Thread.sleep(10000L);
                return "Hello";
            }
        }, 1);
        future.get();

        assertEquals(Status.TIMED_OUT, future.getStatus());
    }
}
