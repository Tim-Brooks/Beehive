package fault.java;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by timbrooks on 12/5/14.
 */
public class ResilientActionTest {

    private ServiceExecutor serviceExecutor;

    @Before
    public void setUp() {
        serviceExecutor = new ServiceExecutor(1);
    }

    @After
    public void tearDown() {
        serviceExecutor.shutdown();
    }

    @Test
    public void testActionSuccess() throws Exception {
        ResilientAction<String> successAction = new SuccessAction();
        ResilientPromise<String> promise = serviceExecutor.performAction(successAction, 25);

        assertEquals("Success", promise.awaitResult());
    }

    @Test
    public void testActionTimeout() throws Exception {
        ResilientAction<String> timeoutAction = new TimeoutAction();
        ResilientPromise<String> promise = serviceExecutor.performAction(timeoutAction, 25);

        assertNull(promise.awaitResult());
        assertEquals(Status.TIMED_OUT, promise.status);
    }

    private class SuccessAction implements ResilientAction<String> {
        @Override
        public String run() throws Exception {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Thread.sleep(random.nextInt(15));
            return "Success";
        }
    }

    private class ErrorAction implements ResilientAction<String> {
        @Override
        public String run() throws Exception {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Thread.sleep(random.nextInt(15));
            throw new IOException("IO Issue");
        }
    }

    private class TimeoutAction implements ResilientAction<String> {
        @Override
        public String run() throws Exception {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Thread.sleep(random.nextInt(30, 50));
            return "Timeout";
        }
    }
}
