package fault;

import fault.metrics.ActionMetrics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

/**
 * Created by timbrooks on 12/5/14.
 */
public class ResilientActionTest {

    private ServiceExecutor serviceExecutor;

    @Before
    public void setUp() {
        serviceExecutor = new BlockingExecutor(1);
    }

    @After
    public void tearDown() {
        serviceExecutor.shutdown();
    }

    @Test
    public void testActionSuccess() throws Exception {
        ResilientAction<String> successAction = new SuccessAction(1);
        ResilientFuture<String> future = serviceExecutor.submitAction(successAction, 25);

        assertEquals("Success-1", future.get());
        assertEquals(Status.SUCCESS, future.getStatus());

        testMetricsResult(1, 0, 0);
    }

    @Test
    public void testActionError() throws Exception {
        ResilientAction<String> errorAction = new ErrorAction(1);
        ResilientFuture<String> future = serviceExecutor.submitAction(errorAction, 25);

        try {
            future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof IOException);
            assertEquals("IO Issue-1", cause.getMessage());
        }
        assertEquals(Status.ERROR, future.getStatus());
        testMetricsResult(0, 1, 0);
    }

    @Test
    public void testActionTimeout() throws Exception {
        ResilientAction<String> timeoutAction = new TimeoutAction();
        ResilientFuture<String> future = serviceExecutor.submitAction(timeoutAction, 25);

        assertNull(future.get());
        assertEquals(Status.TIMED_OUT, future.getStatus());

        testMetricsResult(0, 0, 1);
    }

    @Test
    public void testManyActions() throws Exception {
        serviceExecutor.shutdown();
        serviceExecutor = new EventLoopExecutor(50);
        Random random = new Random();
        int successCount = 0;
        int errorCount = 0;
        int timeoutCount = 0;
        List<ResilientFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 50; ++i) {
            int decider = random.nextInt(3);
            if (decider == 0) {
                futures.add(serviceExecutor.submitAction(new SuccessAction(successCount), 25));
                ++successCount;
            } else if (decider == 1) {
                futures.add(serviceExecutor.submitAction(new ErrorAction(errorCount), 25));
                ++errorCount;
            } else {
                futures.add(serviceExecutor.submitAction(new TimeoutAction(), 25));
                ++timeoutCount;
            }
        }

        int successesRealized = 0;
        int errorsRealized = 0;
        int timeoutsRealized = 0;
        for (ResilientFuture<String> future : futures) {
            try {
                String result = future.get();
                if (future.getStatus() == Status.SUCCESS) {
                    assertEquals("Success-" + successesRealized, result);
                    ++successesRealized;
                } else {
                    assertEquals(Status.TIMED_OUT, future.getStatus());
                    assertNull(result);
                    ++timeoutsRealized;
                }
            } catch (ExecutionException e) {
                Throwable error = e.getCause();
                assertTrue(error instanceof IOException);
                assertEquals("IO Issue-" + errorsRealized, error.getMessage());
                ++errorsRealized;
            }
        }
        assertEquals(successCount, successesRealized);
        assertEquals(errorCount, errorsRealized);
        assertEquals(timeoutCount, timeoutsRealized);

        testMetricsResult(successCount, errorCount, timeoutCount);
    }

    private void testMetricsResult(int success, int errors, int timeouts) throws Exception {
        ActionMetrics actionMetrics = serviceExecutor.getActionMetrics();
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
            Thread.sleep(random.nextInt(45, 55));
            return "Timeout";
        }
    }
}
