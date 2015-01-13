package fault;

import fault.utils.TestActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;
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
            assertEquals(RejectedActionException.Reason.MAX_CONCURRENCY_LEVEL_EXCEEDED, e.reason);
        }
        try {
            blockingExecutor.performAction(TestActions.successAction(1));
            fail();
        } catch (RejectedActionException e) {
            assertEquals(RejectedActionException.Reason.MAX_CONCURRENCY_LEVEL_EXCEEDED, e.reason);
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
        assertEquals(blockingExecutor.getExecutorUUID(), promise.getCompletedBy());
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
            assertNull(promise.getCompletedBy());
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

    // @TODO Write tests for metrics. And tests that ensure metrics are updated even if a different service completes
    // @TODO promise. Add circuit breaker tests. Add queue limit test? Is that possible?
}
