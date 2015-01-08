package fault;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * Created by timbrooks on 1/7/15.
 */
public class BlockingExecutorTest {

    private BlockingExecutor blockingExecutor;

    @Before
    public void setUp() {
        blockingExecutor = new BlockingExecutor(1);
    }

    @Test
    public void testTimeoutScheduled() throws Exception {
        ResilientPromise<String> promise = blockingExecutor.performAction(new ResilientAction<String>() {
            @Override
            public String run() throws Exception {
                Thread.sleep(10000L);
                return "Hello";
            }
        }, 1);
        promise.await();

        assertEquals(Status.TIMED_OUT, promise.getStatus());
    }
}
