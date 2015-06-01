package fault;

import fault.circuit.BreakerConfig;
import fault.circuit.CircuitBreaker;
import fault.circuit.DefaultCircuitBreaker;
import fault.concurrent.MultipleWriterResilientPromise;
import fault.concurrent.ResilientFuture;
import fault.concurrent.ResilientPromise;
import fault.metrics.ActionMetrics;
import fault.metrics.SingleWriterActionMetrics;
import fault.utils.TestActions;
import fault.utils.TestCallbacks;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;


/**
 * Created by timbrooks on 1/7/15.
 */
public class BlockingExecutorTest {

    private ServiceExecutor blockingExecutor;

    @Before
    public void setUp() {
        blockingExecutor = new BlockingExecutor(1, 30);
    }

    @After
    public void tearDown() {
        blockingExecutor.shutdown();
    }

    @Test
    public void actionNotScheduledIfMaxConcurrencyLevelViolated() {
        blockingExecutor = new BlockingExecutor(1, 2);
        CountDownLatch latch = new CountDownLatch(1);
        blockingExecutor.submitAction(TestActions.blockedAction(latch), Long.MAX_VALUE);
        blockingExecutor.submitAction(TestActions.blockedAction(latch), Long.MAX_VALUE);

        try {
            blockingExecutor.submitAction(TestActions.successAction(1), Long.MAX_VALUE);
            fail();
        } catch (RejectedActionException e) {
            assertEquals(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED, e.reason);
        }
        try {
            blockingExecutor.performAction(TestActions.successAction(1));
            fail();
        } catch (RejectedActionException e) {
            assertEquals(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED, e.reason);
        }
        latch.countDown();
    }

    @Test
    public void actionsReleaseSemaphorePermitWhenComplete() throws Exception {
        blockingExecutor = new BlockingExecutor(1, 1);
        int iterations = new Random().nextInt(50);
        for (int i = 0; i < iterations; ++i) {
            ResilientFuture<String> future = blockingExecutor.submitAction(TestActions.successAction(1), 500);
            future.get();
            for (int j = 0; j < 5; ++j) {
                try {
                    blockingExecutor.performAction(TestActions.successAction(1));
                    break;
                } catch (RejectedActionException e) {
                    if (j == 4) {
                        throw e;
                    }
                }
            }
        }
    }

    @Test
    public void actionIsSubmittedAndRan() throws Exception {
        ResilientFuture<String> f = blockingExecutor.submitAction(TestActions.successAction(1), 500);

        assertEquals("Success", f.get());
        assertEquals(Status.SUCCESS, f.getStatus());
    }

    @Test
    public void futureIsPendingUntilSubmittedActionFinished() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ResilientFuture<String> f = blockingExecutor.submitAction(TestActions.blockedAction(latch), Long.MAX_VALUE);
        assertEquals(Status.PENDING, f.getStatus());
        latch.countDown();
        f.get();
        assertEquals(Status.SUCCESS, f.getStatus());
    }

    @Test
    public void performActionCompletesAction() throws Exception {
        ResilientPromise<String> promise = blockingExecutor.performAction(TestActions.successAction(1));
        assertEquals("Success", promise.getResult());
    }

    @Test
    public void promisePassedToExecutorWillBeCompleted() throws Exception {
        MultipleWriterResilientPromise<String> promise = new MultipleWriterResilientPromise<>();
        ResilientFuture<String> f = blockingExecutor.submitAction(TestActions.successAction(50, "Same Promise"),
                promise, Long.MAX_VALUE);

        assertEquals("Same Promise", promise.awaitResult());
        assertEquals(promise, f.promise);
    }

    @Test
    public void promiseWillNotBeCompletedTwice() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        MultipleWriterResilientPromise<String> promise = new MultipleWriterResilientPromise<>();

        blockingExecutor.submitAction(TestActions.blockedAction(latch), promise, Long.MAX_VALUE);

        promise.deliverResult("CompleteOnThisThread");
        latch.countDown();

        for (int i = 0; i < 10; ++i) {
            Thread.sleep(5);
            assertEquals("CompleteOnThisThread", promise.awaitResult());
        }
    }

    @Test
    public void submittedActionWillTimeout() throws Exception {
        ResilientFuture<String> future = blockingExecutor.submitAction(TestActions.blockedAction(new CountDownLatch
                (1)), 1);

        assertNull(future.get());
        assertEquals(Status.TIMED_OUT, future.getStatus());
    }

    @Test
    public void erredActionWillReturnedException() {
        RuntimeException exception = new RuntimeException();
        ResilientFuture<String> future = blockingExecutor.submitAction(TestActions.erredAction(exception), 100);

        try {
            future.get();
            fail();
        } catch (InterruptedException e) {
            fail();
        } catch (ExecutionException e) {
            assertEquals(exception, e.getCause());
        }
        assertEquals(Status.ERROR, future.getStatus());
    }

    @Test
    public void attachedCallbacksWillBeExecutedOnCompletion() throws Exception {
        ResilientPromise<ResilientPromise<String>> errorPromise = new MultipleWriterResilientPromise<>();
        ResilientPromise<ResilientPromise<String>> successPromise = new MultipleWriterResilientPromise<>();
        ResilientPromise<ResilientPromise<String>> timeOutPromise = new MultipleWriterResilientPromise<>();


        CountDownLatch blockingLatch = new CountDownLatch(1);

        ResilientFuture<String> errorF = blockingExecutor.submitAction(TestActions.erredAction(new IOException()),
                TestCallbacks.completePromiseCallback(errorPromise), 100);
        ResilientFuture<String> timeOutF = blockingExecutor.submitAction(TestActions.blockedAction(blockingLatch),
                TestCallbacks.completePromiseCallback(timeOutPromise), 1);
        ResilientFuture<String> successF = blockingExecutor.submitAction(TestActions.successAction(50, "Success"),
                TestCallbacks.completePromiseCallback(successPromise), Long.MAX_VALUE);

        errorPromise.await();
        successPromise.await();
        timeOutPromise.await();
        blockingLatch.countDown();

        assertEquals(errorF.promise, errorPromise.getResult());
        assertEquals(successF.promise, successPromise.getResult());
        assertEquals(timeOutF.promise, timeOutPromise.getResult());
    }

    @Test
    public void resultMetricsUpdated() throws Exception {
        CountDownLatch blockingLatch = new CountDownLatch(1);

        ResilientFuture<String> errorF = blockingExecutor.submitAction(TestActions.erredAction(new IOException()), 100);
        ResilientFuture<String> timeOutF = blockingExecutor.submitAction(TestActions.blockedAction(blockingLatch), 1);
        ResilientFuture<String> successF = blockingExecutor.submitAction(TestActions.successAction(50, "Success"),
                Long.MAX_VALUE);

        for (ResilientFuture<String> f : Arrays.asList(errorF, timeOutF, successF)) {
            try {
                f.get();
                f.get();
                f.get();
            } catch (ExecutionException e) {
            }
        }

        ActionMetrics metrics = blockingExecutor.getActionMetrics();
        Map<Object, Integer> expectedCounts = new HashMap<>();
        expectedCounts.put(Status.SUCCESS, 1);
        expectedCounts.put(Status.ERROR, 1);
        expectedCounts.put(Status.TIMED_OUT, 1);

        assertMetrics(metrics, expectedCounts);
    }

    @Test
    public void rejectedMetricsUpdated() throws Exception {
        blockingExecutor = new BlockingExecutor(1, 1);
        CountDownLatch latch = new CountDownLatch(1);
        ResilientFuture<String> f = blockingExecutor.submitAction(TestActions.blockedAction(latch), Long.MAX_VALUE);

        try {
            blockingExecutor.submitAction(TestActions.successAction(1), Long.MAX_VALUE);
        } catch (RejectedActionException e) {
        }

        latch.countDown();
        f.get();
        blockingExecutor.getCircuitBreaker().forceOpen();

        int maxConcurrencyErrors = 1;
        for (int i = 0; i < 5; ++i) {
            try {
                blockingExecutor.submitAction(TestActions.successAction(1), Long.MAX_VALUE);
            } catch (RejectedActionException e) {
                if (e.reason == RejectionReason.CIRCUIT_OPEN) {
                    break;
                } else {
                    maxConcurrencyErrors++;
                }
            }
        }

        ActionMetrics metrics = blockingExecutor.getActionMetrics();
        HashMap<Object, Integer> expectedCounts = new HashMap<>();
        expectedCounts.put(Status.SUCCESS, 1);
        expectedCounts.put(RejectionReason.CIRCUIT_OPEN, 1);
        expectedCounts.put(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED, maxConcurrencyErrors);

        assertMetrics(metrics, expectedCounts);
    }

    @Test
    public void metricsUpdatedEvenIfPromiseAlreadyCompleted() throws Exception {
        CountDownLatch blockingLatch = new CountDownLatch(1);
        ResilientPromise<String> errP = new MultipleWriterResilientPromise<>();
        ResilientPromise<String> timeoutP = new MultipleWriterResilientPromise<>();
        ResilientPromise<String> successP = new MultipleWriterResilientPromise<>();
        errP.deliverResult("Done");
        timeoutP.deliverResult("Done");
        successP.deliverResult("Done");

        blockingExecutor.submitAction(TestActions.erredAction(new IOException()), errP, 100);
        blockingExecutor.submitAction(TestActions.blockedAction(blockingLatch), timeoutP, 1);
        blockingExecutor.submitAction(TestActions.successAction(50, "Success"), successP, Long.MAX_VALUE);

        ActionMetrics metrics = blockingExecutor.getActionMetrics();
        Map<Object, Integer> expectedCounts = new HashMap<>();
        expectedCounts.put(Status.SUCCESS, 1);
        expectedCounts.put(Status.ERROR, 1);
        expectedCounts.put(Status.TIMED_OUT, 1);


        assertMetrics(metrics, expectedCounts);
        blockingLatch.countDown();
    }

    private void assertMetrics(ActionMetrics metrics, Map<Object, Integer> expectedCounts) throws Exception {
        int milliseconds = 5000;

        int expectedErrors = expectedCounts.get(Status.ERROR) == null ? 0 : expectedCounts.get(Status.ERROR);
        int expectedSuccesses = expectedCounts.get(Status.SUCCESS) == null ? 0 : expectedCounts.get(Status.SUCCESS);
        int expectedTimeouts = expectedCounts.get(Status.TIMED_OUT) == null ? 0 : expectedCounts.get(Status.TIMED_OUT);
        int expectedMaxConcurrency = expectedCounts.get(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED) == null ? 0 :
                expectedCounts.get(RejectionReason.MAX_CONCURRENCY_LEVEL_EXCEEDED);
        int expectedCircuitOpen = expectedCounts.get(RejectionReason.CIRCUIT_OPEN) == null ? 0 : expectedCounts.get
                (RejectionReason.CIRCUIT_OPEN);
        int expectedFailures = expectedErrors + expectedTimeouts;
        for (int i = 0; i < 10; ++i) {
            Thread.sleep(5);
            if (expectedErrors == metrics.getErrorsForTimePeriod(milliseconds) && expectedSuccesses == metrics
                    .getSuccessesForTimePeriod(milliseconds) && expectedFailures == metrics.getFailuresForTimePeriod
                    (milliseconds) && expectedTimeouts == metrics.getTimeoutsForTimePeriod(milliseconds) &&
                    expectedCircuitOpen == metrics.getCircuitOpenedRejectionsForTimePeriod(milliseconds) &&
                    expectedMaxConcurrency == metrics.getMaxConcurrencyRejectionsForTimePeriod(milliseconds)) {
                break;
            }
        }

        assertEquals(expectedErrors, metrics.getErrorsForTimePeriod(milliseconds));
        assertEquals(expectedTimeouts, metrics.getTimeoutsForTimePeriod(milliseconds));
        assertEquals(expectedSuccesses, metrics.getSuccessesForTimePeriod(milliseconds));
        assertEquals(expectedFailures, metrics.getFailuresForTimePeriod(milliseconds));
        assertEquals(expectedMaxConcurrency, metrics.getMaxConcurrencyRejectionsForTimePeriod(milliseconds));
        assertEquals(expectedCircuitOpen, metrics.getCircuitOpenedRejectionsForTimePeriod(milliseconds));
        assertEquals(0, metrics.getQueueFullRejectionsForTimePeriod(milliseconds));
    }

    @Test
    public void circuitBreaker() throws Exception {
        BreakerConfig.BreakerConfigBuilder builder = new BreakerConfig.BreakerConfigBuilder();
        builder.timePeriodInMillis = 10000;
        builder.failureThreshold = 5;
        builder.timeToPauseMillis = 50;

        ActionMetrics metrics = new SingleWriterActionMetrics(3600);
        CircuitBreaker breaker = new DefaultCircuitBreaker(metrics, builder.build());
        blockingExecutor = new BlockingExecutor(1, 100, "test", metrics, breaker);

        List<ResilientFuture<String>> fs = new ArrayList<>();
        for (int i = 0; i < 6; ++i) {
            fs.add(blockingExecutor.submitAction(TestActions.erredAction(new RuntimeException()), Long.MAX_VALUE));
        }

        for (ResilientFuture<String> f : fs) {
            try {
                f.get();
            } catch (ExecutionException e) {
            }
        }

        Thread.sleep(10);

        try {
            blockingExecutor.submitAction(TestActions.successAction(0), 100);
            fail("Should have been rejected due to open circuit.");
        } catch (RejectedActionException e) {
            assertEquals(RejectionReason.CIRCUIT_OPEN, e.reason);
        }

        Thread.sleep(150);

        ResilientFuture<String> f = blockingExecutor.submitAction(TestActions.successAction(0, "Result"), 100);
        assertEquals("Result", f.get());

        ResilientFuture<String> fe = blockingExecutor.submitAction(TestActions.erredAction(new RuntimeException()), Long.MAX_VALUE);
        try {
            fe.get();
        } catch (ExecutionException e) {
        }

        Thread.sleep(10);

        try {
            blockingExecutor.submitAction(TestActions.successAction(0), 100);
            fail("Should have been rejected due to open circuit.");
        } catch (RejectedActionException e) {
            assertEquals(RejectionReason.CIRCUIT_OPEN, e.reason);
        }
    }
}
