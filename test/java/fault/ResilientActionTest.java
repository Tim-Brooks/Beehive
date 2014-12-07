package fault;

import fault.metrics.IActionMetrics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

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
        ResilientAction<String> successAction = new SuccessAction(1);
        ResilientPromise<String> promise = serviceExecutor.performAction(successAction, 25);

        assertEquals("Success-1", promise.awaitResult());
        assertEquals(Status.SUCCESS, promise.status);

        testMetricsResult(1, 0, 0);
    }

    @Test
    public void testActionError() throws Exception {
        ResilientAction<String> errorAction = new ErrorAction(1);
        ResilientPromise<String> promise = serviceExecutor.performAction(errorAction, 25);

        assertNull(promise.awaitResult());
        Throwable error = promise.error;
        assertTrue(error instanceof IOException);
        assertEquals("IO Issue-1", error.getMessage());
        assertEquals(Status.ERROR, promise.status);

        testMetricsResult(0, 1, 0);
    }

    @Test
    public void testActionTimeout() throws Exception {
        ResilientAction<String> timeoutAction = new TimeoutAction();
        ResilientPromise<String> promise = serviceExecutor.performAction(timeoutAction, 25);

        assertNull(promise.awaitResult());
        assertEquals(Status.TIMED_OUT, promise.status);

        testMetricsResult(0, 0, 1);
    }

    @Test
    public void testManyActions() throws Exception {
        serviceExecutor = new ServiceExecutor(50);
        Random random = new Random();
        int successCount = 0;
        int errorCount = 0;
        int timeoutCount = 0;
        List<ResilientPromise<String>> promises = new ArrayList<>();
        for (int i = 0; i < 50; ++i) {
            int decider = random.nextInt(3);
            if (decider == 0) {
                promises.add(serviceExecutor.performAction(new SuccessAction(successCount), 200));
                ++successCount;
            } else if (decider == 1) {
                promises.add(serviceExecutor.performAction(new ErrorAction(errorCount), 200));
                ++errorCount;
            } else {
                promises.add(serviceExecutor.performAction(new TimeoutAction(), 25));
                ++timeoutCount;
            }
        }

        int successesRealized = 0;
        int errorsRealized = 0;
        int timeoutsRealized = 0;
        for (ResilientPromise<String> promise : promises) {
            promise.await();
            if (promise.status == Status.SUCCESS) {
                assertEquals("Success-" + successesRealized, promise.result);
                assertNull(promise.error);
                ++successesRealized;
            } else if (promise.status == Status.ERROR) {
                Throwable error = promise.error;
                assertTrue(error instanceof IOException);
                assertEquals("IO Issue-" + errorsRealized, error.getMessage());
                assertNull(promise.result);
                ++errorsRealized;
            } else if (promise.status == Status.TIMED_OUT) {
                assertNull(promise.result);
                assertNull(promise.error);
                ++timeoutsRealized;
            } else {
                fail();
            }
        }
        assertEquals(successCount, successesRealized);
        assertEquals(errorCount, errorsRealized);
        assertEquals(timeoutCount, timeoutsRealized);

        testMetricsResult(successCount, errorCount, timeoutCount);
    }

    private void testMetricsResult(int success, int errors, int timeouts) throws Exception {
        IActionMetrics actionMetrics = serviceExecutor.getActionMetrics();
        int successCount = Integer.MIN_VALUE;
        int errorCount = Integer.MIN_VALUE;
        int timeoutCount = Integer.MIN_VALUE;
        for (int i = 0; i < 5; ++i) {
            Thread.sleep(50);
            successCount = actionMetrics.getSuccessesForTimePeriod(60000);
            errorCount = actionMetrics.getErrorsForTimePeriod(60000);
            timeoutCount = actionMetrics.getTimeoutsForTimePeriod(60000);
            if (errorCount == errors && successCount == success && timeoutCount == timeouts) {
                break;
            }
        }
        assertEquals(success, successCount);
        assertEquals(errors, errorCount);
        assertEquals(timeouts, timeoutCount);
        assertEquals(timeouts + errors, actionMetrics.getFailuresForTimePeriod(60000));
    }

    private class SuccessAction implements ResilientAction<String> {
        final int count;

        private SuccessAction(int count) {
            this.count = count;
        }

        @Override
        public String run() throws Exception {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Thread.sleep(random.nextInt(15));
            return "Success-" + count;
        }
    }

    private class ErrorAction implements ResilientAction<String> {
        final int count;

        private ErrorAction(int count) {
            this.count = count;
        }

        @Override
        public String run() throws Exception {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Thread.sleep(random.nextInt(15));
            throw new IOException("IO Issue-" + count);
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
