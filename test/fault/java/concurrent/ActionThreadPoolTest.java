package fault.java.concurrent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by timbrooks on 12/3/14.
 */
public class ActionThreadPoolTest {

    private ActionThreadPool threadPool;

    @Before
    public void setUp() {
        threadPool = new ActionThreadPool(1);
    }

    @After
    public void tearDown() {
        threadPool.shutdown();
    }

    @Test
    public void testPoolRequiresAtLeastOneThread() {
        try {
            new ActionThreadPool(0);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Cannot have fewer than 1 thread", e.getMessage());
        }
    }
}
