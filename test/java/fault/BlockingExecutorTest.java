package fault;

import fault.utils.TestActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


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
    public void futureIsPendingUntilActionFinished() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ResilientFuture<String> f = blockingExecutor.submitAction(TestActions.blockedAction(latch), Long.MAX_VALUE);
        assertEquals(Status.PENDING, f.getStatus());
        latch.countDown();
        f.get();
        assertEquals(Status.SUCCESS, f.getStatus());
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
